package edu.uiuc.immutability;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.text.edits.TextEditGroup;

import edu.uiuc.immutability.ClassConstructorAnalysis.FullConstructorStatus;

public class MakeClassImmutableRewriter {
	private MakeClassImmutableVisitor immutableRewriter;
	
	private final MakeImmutableRefactoring makeImmutableRefactoring;
	private final ICompilationUnit unit;
	private final ASTRewrite rewriter;
	private final AST astRoot;
	private final RewriteUtil rewriteUtil;
	private List<TextEditGroup> groupDescriptions;

	
	public MakeClassImmutableRewriter(MakeImmutableRefactoring makeImmutableRefactoring,
	                                  ICompilationUnit unit, 
	                                  ASTRewrite rewriter) {
		this.makeImmutableRefactoring = makeImmutableRefactoring;
		this.unit = unit;
		this.rewriter = rewriter;
		this.astRoot = rewriter.getAST();
		
		this.rewriteUtil = new RewriteUtil(this.astRoot);
		groupDescriptions = new ArrayList<TextEditGroup>();
	}

	public void rewrite(TypeDeclaration targetClass, 
	                    ClassMutatorAnalysis mutatorAnalysis,
	                    ClassConstructorAnalysis constructorAnalysis) {
		// Add a full constructor (if one is needed)
		if (   constructorAnalysis.getFullConstructorStatus() == FullConstructorStatus.HAS_NOT_FULL_CONSTRUCTOR 
			&& mutatorAnalysis.hasMutators()) {
			MethodDeclaration constructor = rewriteUtil.createFullConstructor(targetClass);
			addNewConstructorToClass(constructor, targetClass);
		}
		
		// Rewrite fields and methods
		immutableRewriter = new MakeClassImmutableVisitor(makeImmutableRefactoring, unit, rewriter, 
		                                                  mutatorAnalysis, rewriteUtil, groupDescriptions);
		targetClass.accept(immutableRewriter);
	}

	public RefactoringStatus getStatus() {
		return (immutableRewriter != null) ? immutableRewriter.getStatus(): null;
	}
	
	@SuppressWarnings("unchecked")
	public Collection getGroupDescriptions() {
		return groupDescriptions;
	}
	
	
	/*************************************************************************/
	/* Private methods */
	
	private void addNewConstructorToClass(MethodDeclaration constructor, TypeDeclaration classDecl) {
		final TextEditGroup newConstructorEdit = 
				new TextEditGroup("creating constructor that initializes all the fields of " +
			                      "the class to use with setters");
		
		ListRewrite classDeclarationsRewrite = 
				rewriter.getListRewrite(classDecl, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
		
		// Try to insert the new constructor after the last current constructor
		MethodDeclaration lastCurrentConstructor = null;
		MethodDeclaration[] methods = classDecl.getMethods();	
		for(int i = methods.length-1; i >= 0; --i) {
			if (methods[i].isConstructor()) {
				lastCurrentConstructor = methods[i];
			}
		}
		
		if (lastCurrentConstructor != null) {
			classDeclarationsRewrite.insertAfter(constructor, lastCurrentConstructor, newConstructorEdit);
		}
		else {
			if (methods.length > 0) {
				// No constructors exist so we insert it before the first method
				classDeclarationsRewrite.insertBefore(constructor, methods[0], newConstructorEdit);
			}
			else {
				classDeclarationsRewrite.insertLast(constructor, newConstructorEdit);	
			}
		}
		
		groupDescriptions.add(newConstructorEdit);
	}
}
