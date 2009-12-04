package edu.uiuc.immutability;

import java.beans.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.Modifier.ModifierKeyword;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.dom.ModifierRewrite;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.text.edits.TextEditGroup;

import edu.uiuc.immutability.analysis.ClassMutatorAnalysis;

@SuppressWarnings("restriction")
public class MakeClassImmutableVisitor extends ASTVisitor {

	private RefactoringStatus status;
	private List<TextEditGroup> groupDescriptions;
	private final MakeImmutableRefactoring refactoring;
	private final ASTRewrite rewriter;
	private final AST astRoot;
	private final ICompilationUnit unit;
	private final ClassMutatorAnalysis mutatorAnalysis;
	private final RewriteUtil rewriteUtil;
	
	public MakeClassImmutableVisitor(MakeImmutableRefactoring makeImmutableRefactoring,
	                                     ICompilationUnit unit,
	                                     ASTRewrite rewriter,
	                                     ClassMutatorAnalysis mutatorAnalysis,
	                                     RewriteUtil rewriteUtil,
	                                     List<TextEditGroup> groupDescriptions) {
		this.refactoring = makeImmutableRefactoring;
		this.rewriter = rewriter;
		this.mutatorAnalysis = mutatorAnalysis;
		this.rewriteUtil = rewriteUtil;
		this.groupDescriptions = groupDescriptions;
		this.astRoot = rewriter.getAST();
		this.unit = unit;
		status = new RefactoringStatus();
		
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public boolean visit(MethodDeclaration methodDecl) {
		if ( doesParentBindToTargetClass (methodDecl) && !methodDecl.isConstructor() ) {
			final TextEditGroup editGroup = new TextEditGroup("replace setter with factory method");
			
			List<SimpleName> fieldAssignments = mutatorAnalysis.getFieldAssignments(methodDecl); 
			
			// If there are any field assignments in the function then we must introduce temporaries to write these
			// and then create a new object from the temporaries that we return
			if (!fieldAssignments.isEmpty()) {
				TypeDeclaration declaringClass = (TypeDeclaration) ASTNodes.getParent(methodDecl, TypeDeclaration.class);
				String classIdentifier = declaringClass.getName().getIdentifier();
				Block methodBody = methodDecl.getBody();
				SimpleType classType = astRoot.newSimpleType(astRoot.newName(new String[] {classIdentifier}));
				
				
				/* Create a reference to the new object and set it to this */
				SimpleName newThis = rewriteUtil.getThisSimpleName();
				VariableDeclarationFragment newThisFragment = astRoot.newVariableDeclarationFragment();
				newThisFragment.setName(newThis);
				ThisExpression oldThis = astRoot.newThisExpression();
				newThisFragment.setInitializer(oldThis);
				
				VariableDeclarationStatement newThisDeclaration = astRoot.newVariableDeclarationStatement(newThisFragment);
				Type newThisType = (SimpleType) ASTNode.copySubtree(astRoot, classType);
				newThisDeclaration.setType(newThisType);
				
				rewriter.getListRewrite(methodBody, Block.STATEMENTS_PROPERTY).insertFirst(newThisDeclaration, editGroup);
								
				
				/* Replace every write to a field with the creation of a new object */
				for (SimpleName simpleName : fieldAssignments) {
					Assignment fieldAssignment = (Assignment) ASTNodes.getParent(simpleName, Assignment.class);
					assert fieldAssignment != null;
					
					List<Expression> arguments = new ArrayList<Expression>();
					
					FieldDeclaration[] fields = declaringClass.getFields();
					for (FieldDeclaration field : fields) {
						List fragments = field.fragments();
						for (Object fragmentObject : fragments) {
							VariableDeclarationFragment fragment = (VariableDeclarationFragment) fragmentObject;
							assert fragment != null;
							
							if (fragment.getName().getIdentifier().equals(simpleName.getIdentifier())) {
								Expression assignValue = 
										(Expression) ASTNode.copySubtree(astRoot, fieldAssignment.getRightHandSide()); 
								arguments.add(assignValue);
							}
							else {
								FieldAccess fieldAccessExpression = astRoot.newFieldAccess();
								
								ThisExpression thisExpression = astRoot.newThisExpression();
								fieldAccessExpression.setExpression(thisExpression);
								
								SimpleName fieldName = (SimpleName) ASTNode.copySubtree(astRoot, fragment.getName());
								fieldAccessExpression.setName(fieldName);
								
								arguments.add(fieldAccessExpression);
							}
						}
					}
					
					Expression mutateExpression = rewriteUtil.createMutateExpression(classType, arguments);
					
					if (fieldAssignment.getParent() instanceof ExpressionStatement ) {
						rewriter.replace(fieldAssignment, mutateExpression, editGroup);							
					}
				}
				
				
				/* Replace void return type with an object of this class' type */ 
				SimpleType returnType = (SimpleType) ASTNode.copySubtree(astRoot, classType);
				Type oldReturnType = methodDecl.getReturnType2();
				if (oldReturnType != null) {
					rewriter.replace(oldReturnType, returnType, editGroup);					
				}
				else {
					methodDecl.setReturnType2(returnType);
				}
				

				/* Add a return statement to the end returning the _this object */
				ReturnStatement returnStatement = astRoot.newReturnStatement();
				returnStatement.setExpression(rewriteUtil.getThisSimpleName());				
				rewriter.getListRewrite(methodBody, Block.STATEMENTS_PROPERTY).insertLast(returnStatement, editGroup);
				
				groupDescriptions.add(editGroup);
			}
		}
		return false;
	}
	
	private boolean doesParentBindToTargetClass(MethodDeclaration methodDecl) {
		TypeDeclaration declaringClass = (TypeDeclaration) ASTNodes.getParent(methodDecl, TypeDeclaration.class);
		return Bindings.equals(declaringClass.resolveBinding(), refactoring.getTargetBinding());
	}

	@SuppressWarnings("unchecked")
	@Override
	public boolean visit(FieldDeclaration fieldDecl) {
		if (doesParentBindToTargetClass(fieldDecl)) {
						
			// Change modifier to final
			if (!Flags.isFinal(fieldDecl.getModifiers())) {
				TextEditGroup gd = new TextEditGroup("change field to final and add initializer(s)");

				int finalModifiers = fieldDecl.getModifiers() | ModifierKeyword.FINAL_KEYWORD.toFlagValue();
				ModifierRewrite.create(rewriter, fieldDecl).setModifiers(finalModifiers, gd);
				
				// Add initializers to the fragments that do not have them as they are required for final variables
				Type fieldDeclType = fieldDecl.getType(); 
				List fragments = fieldDecl.fragments();
				for (Object obj : fragments) {
					VariableDeclarationFragment frag = (VariableDeclarationFragment)obj;
					
					// Check whether the field is already initialized
					if (frag != null && frag.getInitializer() == null) {
						
						// If the field is already initialized in a constructor or we have mutators (in which case we 
						// will have made new constructors) we can't initialize it a second time at the 
						// declaration point
						if ( !mutatorAnalysis.hasMutators() && !isFieldInitializedInConstructor(fieldDeclType, frag, gd) ) {
	
							// Add initializer
							Expression initializer = rewriteUtil.createDefaultInitializer(fieldDeclType);
							if (initializer == null) continue;
							
							VariableDeclarationFragment newFrag =
									(VariableDeclarationFragment)ASTNode.copySubtree(astRoot, frag);
							newFrag.setInitializer(initializer);
							rewriter.replace(frag, newFrag, gd);
						}
					}
					else {
						// If we have added a constructor we must make sure the field is not initialized in the declaration
						if (mutatorAnalysis.hasMutators()) {
							VariableDeclarationFragment newFrag =
									(VariableDeclarationFragment)ASTNode.copySubtree(astRoot, frag);
							newFrag.setInitializer(null);
							rewriter.replace(frag, newFrag, gd);
						}
					}
				}
				
				groupDescriptions.add(gd);
			}

			// Change access to private
			if (!Flags.isPrivate(fieldDecl.getModifiers())) {
				TextEditGroup gd2 = new TextEditGroup("Change field to private");

				int privateModifiers = fieldDecl.getModifiers() | ModifierKeyword.PRIVATE_KEYWORD.toFlagValue();
				ModifierRewrite.create(rewriter, fieldDecl).setModifiers(privateModifiers, gd2);
				
				groupDescriptions.add(gd2);
			}
		}

		return false;
	}
	
	@SuppressWarnings("unchecked")
	private boolean isFieldInitializedInConstructor(Type fieldDeclType, VariableDeclarationFragment frag, TextEditGroup gd) {
		boolean isInitializedInConstructor = false;
		
		// Get the class of the variable (The grandparent of a field/fragment is always its class)
		TypeDeclaration parentClass = (TypeDeclaration)frag.getParent().getParent();
		assert parentClass.isInterface() == false; //Interfaces can't have non-static fields
		
		// Go through all the constructors and check whether they initialize the variable
		IType parentClassType = unit.getType(parentClass.getName().toString());
		IField field = parentClassType.getField(frag.getName().toString());
		
		MethodDeclaration[] methodDecls;
		IMethod[] methods;
		try {
			List<MethodDeclaration> methodDeclsWithoutInitialization = new ArrayList<MethodDeclaration>();
		
			// We take advantage of the fact that the method lists of both IType and TypeDeclaration are ordered if
			// it is a source file (which are the only files we transform) to build a list of the MethodDeclarations
			// of constructors that don't initialize the current field
			methodDecls = parentClass.getMethods();
			methods = parentClassType.getMethods();
			assert methodDecls.length == methods.length;
			for (int i = 0; i < methods.length; ++i ) {
				IMethod method = methods[i];
				MethodDeclaration methodDecl = methodDecls[i];
				
				if (method.isConstructor()) {
					final List matches = getWritesToFieldInMethod(field, method);
					if (!matches.isEmpty()) {
						isInitializedInConstructor = true;
					}
					else {
						methodDeclsWithoutInitialization.add(methodDecl);
					}
				}
			}
			
			if ( isInitializedInConstructor ) {

				// At least one constructor initializes the variable so it won't be initialized in the declaration
				// and we must add initialization statements to any constructors that lack it
				for ( MethodDeclaration methodDecl : methodDeclsWithoutInitialization ) {
					
					// Create an assignment of the default value to frag in methodDecl
					Expression initializer = rewriteUtil.createDefaultInitializer(fieldDeclType);
					ExpressionStatement assignStmt = 
							rewriteUtil.createFieldAssignmentStatement(fieldDeclType, frag, initializer);
					
					// Add the assign statement to the method body
					rewriter.getListRewrite(methodDecl.getBody(), Block.STATEMENTS_PROPERTY).insertLast(assignStmt, gd);
				}
				
				return true;
			}
			
		} catch (JavaModelException e) {
			// TODO: Trigger error
		} catch (CoreException e) {
			// TODO: Trigger error
		}
		
		return false;
	}

	@SuppressWarnings("unchecked")
	private List getWritesToFieldInMethod(IField field, IMethod method)
			throws CoreException {
		SearchPattern pattern = SearchPattern.createPattern(field, IJavaSearchConstants.WRITE_ACCESSES);
		SearchEngine engine = new SearchEngine();
		SearchParticipant[] participants = new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() };
		IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[] { method });
		
		final List matches = new ArrayList();
		SearchRequestor requestor = new SearchRequestor() {
			public void acceptSearchMatch(SearchMatch match) {
				matches.add(match);
			}
		};
		
		engine.search(pattern, participants, scope, requestor, null);
		return matches;
	}
	
	private boolean doesParentBindToTargetClass(FieldDeclaration fieldDecl) {
		ASTNode parent = ASTNodes.getParent(fieldDecl, TypeDeclaration.class);
		if (parent != null) {
			TypeDeclaration typeDecl = (TypeDeclaration) parent;
			return Bindings.equals(typeDecl.resolveBinding(), refactoring.getTargetBinding());
		}
		return false;
	}
	

	public RefactoringStatus getStatus() {
		// TODO Auto-generated method stub
		return status;
	}
}
