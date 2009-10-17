package edu.uiuc.immutability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.Message;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.refactoring.descriptors.JavaRefactoringDescriptor;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.internal.core.util.ASTNodeFinder;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.NodeFinder;
import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringScopeFactory;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringSearchEngine;
import org.eclipse.jdt.internal.corext.refactoring.base.JavaStatusContext;
import org.eclipse.jdt.internal.corext.refactoring.changes.DynamicValidationRefactoringChange;
import org.eclipse.jdt.internal.corext.refactoring.structure.ASTNodeSearchUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.internal.corext.refactoring.util.ResourceUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.TextChangeManager;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.participants.ResourceChangeChecker;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.TextEditGroup;


public class MakeImmutableRefactoring extends Refactoring {

	private static final String refactoringName = "Make Immutable Class";
	private final IType targetClass;
	private ITypeBinding targetBinding;
	private TextChangeManager fChangeManager;
	private CompilationUnit fRoot;
	private ASTRewrite fRewriter;

	public MakeImmutableRefactoring(IType targetClass) {
		this.targetClass = targetClass;
		fChangeManager= new TextChangeManager();
	}

	@Override
	public RefactoringStatus checkFinalConditions(IProgressMonitor pm)
			throws CoreException, OperationCanceledException {
		RefactoringStatus result= new RefactoringStatus();
		fChangeManager.clear();
		pm.beginTask("", 12);
		pm.setTaskName("Convert to AtomicInteger checking preconditions");
		pm.worked(1);
		if (result.hasFatalError())
			return result;
		pm.setTaskName("ConvertToAtomicInteger searching for cunits"); 
		final SubProgressMonitor subPm= new SubProgressMonitor(pm, 5);
		ICompilationUnit[] affectedCUs= RefactoringSearchEngine.findAffectedCompilationUnits(
			SearchPattern.createPattern(targetClass, IJavaSearchConstants.ALL_OCCURRENCES),
			RefactoringScopeFactory.create(targetClass, true),
			subPm,
			result, true);
		
		if (result.hasFatalError())
			return result;
			
		pm.setTaskName("Analyzing the field");	 
		IProgressMonitor sub= new SubProgressMonitor(pm, 5);
		sub.beginTask("", affectedCUs.length);
		
		List ownerDescriptions= new ArrayList();
		ICompilationUnit owner= targetClass.getCompilationUnit();
		
		for (int i= 0; i < affectedCUs.length; i++) {
			ICompilationUnit unit= affectedCUs[i];
			sub.subTask(unit.getElementName());
			CompilationUnit root= null;
			ASTRewrite rewriter= null;
			List descriptions;
			if (owner.equals(unit)) {
				root= fRoot;
				rewriter= fRewriter;
				descriptions= ownerDescriptions;
			} else {
				root= new RefactoringASTParser(AST.JLS3).parse(unit, true);
				rewriter= ASTRewrite.create(root.getAST());
				descriptions= new ArrayList();
			}
			checkCompileErrors(result, root, unit);
			AccessAnalyzerForImmutability analyzer= new AccessAnalyzerForImmutability(this, unit, rewriter);
			root.accept(analyzer);
			result.merge(analyzer.getStatus());
			if (result.hasFatalError()) {
				fChangeManager.clear();
				return result;
			}
			descriptions.addAll(analyzer.getGroupDescriptions());
			if (!owner.equals(unit))
				createEdits(unit, rewriter, descriptions);
			sub.worked(1);
			if (pm.isCanceled())
				throw new OperationCanceledException();
		}
		
		createEdits(owner, fRewriter, ownerDescriptions);
		
		sub.done();
		IFile[] filesToBeModified= ResourceUtil.getFiles(fChangeManager.getAllCompilationUnits());
		result.merge(Checks.validateModifiesFiles(filesToBeModified, getValidationContext()));
		if (result.hasFatalError())
			return result;
		ResourceChangeChecker.checkFilesToBeChanged(filesToBeModified, new SubProgressMonitor(pm, 1));
		return result;
	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return refactoringName;
	}
	
	private void createEdits(ICompilationUnit unit, ASTRewrite rewriter,
			List<TextEditGroup> descriptions) throws CoreException {
		TextChange change= fChangeManager.get(unit);
		MultiTextEdit root= new MultiTextEdit();
		change.setEdit(root);

		for (TextEditGroup group : descriptions)
			change.addTextEditGroup(group);

		root.addChild(rewriter.rewriteAST());
	}

	@Override
	public RefactoringStatus checkInitialConditions(IProgressMonitor pm)
	throws CoreException, OperationCanceledException {

		RefactoringStatus result=  new RefactoringStatus();
		result.merge(Checks.checkAvailability(targetClass));

		if (result.hasFatalError())
			return result;

		fRoot = new RefactoringASTParser(AST.JLS3).parse(targetClass.getCompilationUnit(), true, pm);
		ISourceRange sourceRange= targetClass.getNameRange();
		
		ASTNode node= NodeFinder.perform(fRoot, sourceRange.getOffset(), sourceRange.getLength());
		if (node == null) {
			return mappingErrorFound(result, node);
		} else {
			TypeDeclaration typeDeclaration = ASTNodeSearchUtil.getTypeDeclarationNode(targetClass, fRoot);
			targetBinding = typeDeclaration.resolveBinding();
		}
		fRewriter= ASTRewrite.create(fRoot.getAST());
		return result;
	}
	

	@Override
	public Change createChange(IProgressMonitor pm) throws CoreException,
	OperationCanceledException {
		String project= null;
		IJavaProject javaProject= targetClass.getJavaProject();
		if (javaProject != null)
			project= javaProject.getElementName();
		int flags= JavaRefactoringDescriptor.JAR_MIGRATION | JavaRefactoringDescriptor.JAR_REFACTORING | RefactoringDescriptor.STRUCTURAL_CHANGE | RefactoringDescriptor.MULTI_CHANGE;
		try {
			if (targetClass.isAnonymous() || targetClass.isLocal())
				flags|= JavaRefactoringDescriptor.JAR_SOURCE_ATTACHMENT;
		} catch (JavaModelException exception) {
			JavaPlugin.log(exception);
		}

		//TODO need to properly initialize the arguments so that this refactoring becomes recordable
		final Map arguments= new HashMap();
		String description = "Convert Mutable into Immutable Class";
		String comment = "Convert Mutable into Immutable Class";

		final JavaRefactoringDescriptor descriptor= new JavaRefactoringDescriptor("fixme", project, description, comment, arguments, flags) {};

		//JDTRefactoringDescriptor(IJavaRefactorings.ENCAPSULATE_FIELD, project, description, comment, arguments, flags);

		final DynamicValidationRefactoringChange result= new DynamicValidationRefactoringChange(descriptor, getName());
		TextChange[] changes= fChangeManager.getAllChanges();
		pm.beginTask("ConvertToParallelArray create changes", changes.length);
		for (int i= 0; i < changes.length; i++) {
			result.add(changes[i]);
			pm.worked(1);
		}
		pm.done();
		return result;
	}
	
	
	private RefactoringStatus mappingErrorFound(RefactoringStatus result, ASTNode node) {
		if (node != null && (node.getFlags() & ASTNode.MALFORMED) != 0 && processCompilerError(result, node))
			return result;
		result.addFatalError(getMappingErrorMessage());
		return result;
	}

	private String getMappingErrorMessage() {
		return Messages.format(
				"Make Immutable Class cannot analyze selected class", 
				new String[] {targetClass.getElementName()});
	}

	private boolean processCompilerError(RefactoringStatus result, ASTNode node) {
		Message[] messages= ASTNodes.getMessages(node, ASTNodes.INCLUDE_ALL_PARENTS);
		if (messages.length == 0)
			return false;
		result.addFatalError(Messages.format(
				"Compiler errors with the class to be refactored",  
				new String[] { targetClass.getElementName(), messages[0].getMessage()}));
		return true;
	}

	private void checkCompileErrors(RefactoringStatus result, CompilationUnit root, ICompilationUnit element) {
		IProblem[] messages= root.getProblems();
		for (int i= 0; i < messages.length; i++) {
			IProblem problem= messages[i];
			if (!isIgnorableProblem(problem)) {
				result.addError(Messages.format(
						"MakeImmutableClass: Compiler errors", 
						element.getElementName()), JavaStatusContext.create(element));
				return;
			}
		}
	}
	
	private boolean isIgnorableProblem(IProblem problem) {
		return (problem.getID() == IProblem.NotVisibleType);
	}

	public IBinding getTargetBinding() {
		return targetBinding;
	}
	

}
