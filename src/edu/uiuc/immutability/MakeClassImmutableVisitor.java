package edu.uiuc.immutability;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.Modifier.ModifierKeyword;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
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

@SuppressWarnings("restriction")
public class MakeClassImmutableVisitor extends ASTVisitor {

	private RefactoringStatus status;
	private List<TextEditGroup> groupDescriptions;
	private final MakeImmutableRefactoring refactoring;
	private final ASTRewrite rewriter;
	private final AST astRoot;
	private final ICompilationUnit unit;
	private final ClassMutatorAnalysis mutatorAnalysis;
	private final ClassRewriteUtil rewriteUtil;
	
	public MakeClassImmutableVisitor(MakeImmutableRefactoring makeImmutableRefactoring,
	                                     ICompilationUnit unit,
	                                     ASTRewrite rewriter,
	                                     ClassMutatorAnalysis mutatorAnalysis,
	                                     ClassRewriteUtil rewriteUtil,
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
			
			// Find and remove assignment expressions
			final FieldWritesVisitor fieldWritesVisitor = new FieldWritesVisitor(rewriter, editGroup);
			methodDecl.accept(fieldWritesVisitor);
			
			// If we found any then we must convert the function into a factory method by returning an object
			// of the class type
			if (! fieldWritesVisitor.getRemovedExpressionStatements().isEmpty()) {
				
				TypeDeclaration declaringClass = (TypeDeclaration) ASTNodes.getParent(methodDecl, TypeDeclaration.class);
				String classIdentifier = declaringClass.getName().getIdentifier();
				
				// Replace void return type with an object of this class' type 
				SimpleType returnType = astRoot.newSimpleType(astRoot.newName(new String[] {classIdentifier}));
				Type oldReturnType = methodDecl.getReturnType2();
				if (oldReturnType != null) {
					rewriter.replace(oldReturnType, returnType, editGroup);					
				}
				else {
					methodDecl.setReturnType2(returnType);
				}
				
				// Add return statement returning a newly created object
				List<String> types = new ArrayList<String>();
				FieldDeclaration[] fields = declaringClass.getFields();
				ClassInstanceCreation returnObjectCreation = astRoot.newClassInstanceCreation();
				returnObjectCreation.setType(astRoot.newSimpleType(astRoot.newName(new String[] {classIdentifier})));
				for (FieldDeclaration field : fields) {
					List fragments = field.fragments();
					for (Object fragmentObject : fragments) {
						VariableDeclarationFragment fragment = (VariableDeclarationFragment) fragmentObject;
						assert fragment != null;
						
						types.add(field.getType().toString());
						
						Expression constructorArgumentExpr = null;
						
						List<ExpressionStatement> removedExpressionStatements = fieldWritesVisitor.getRemovedExpressionStatements();
						for (int i = removedExpressionStatements.size()-1; i >= 0; --i) {
							ExpressionStatement removedExpressionStatement = removedExpressionStatements.get(i);
							Assignment assignment = (Assignment) removedExpressionStatement.getExpression();
							
							Assignment currentAssignment = assignment;

							do {
								Expression lhs = currentAssignment.getLeftHandSide();
								Expression rhs = currentAssignment.getRightHandSide();
								
								if (lhs instanceof FieldAccess) {
									FieldAccess accessStmt = (FieldAccess)lhs;
									lhs = accessStmt.getName();
								}
								
								assert lhs instanceof SimpleName; 
								SimpleName lhsName = (SimpleName)lhs;
									
								if (lhsName.getIdentifier().equals(fragment.getName().getIdentifier()) ) {
									while (rhs instanceof Assignment) {
										rhs = ((Assignment)rhs).getRightHandSide();
									}
									
									constructorArgumentExpr = rhs;
									break;
								}
								
								if (rhs instanceof Assignment) {
									currentAssignment = (Assignment)rhs; 
								}
								else {
									currentAssignment = null; 
								}
							} while( currentAssignment != null );
						}
	
						if ( constructorArgumentExpr == null) {
							FieldAccess fieldAccessExpression = astRoot.newFieldAccess();
							
							ThisExpression thisExpression = astRoot.newThisExpression();
							fieldAccessExpression.setExpression(thisExpression);
							
							SimpleName fieldName = (SimpleName) ASTNode.copySubtree(astRoot, fragment.getName());
							fieldAccessExpression.setName(fieldName);
							
							constructorArgumentExpr = fieldAccessExpression;
						}
						
						returnObjectCreation.arguments().add(ASTNode.copySubtree(astRoot, constructorArgumentExpr));
					}
				}

				ReturnStatement returnStatement = astRoot.newReturnStatement();
				returnStatement.setExpression(returnObjectCreation);				
				rewriter.getListRewrite(methodDecl.getBody(), Block.STATEMENTS_PROPERTY).insertLast(returnStatement, editGroup);
				
				groupDescriptions.add(editGroup);
			}
		}
		return false;
	}
	
	@SuppressWarnings("unchecked")
	private boolean doesClassHaveConstructorWithTypes(TypeDeclaration classDecl, List<String> types) {
		MethodDeclaration[] methods = classDecl.getMethods();
		for (MethodDeclaration method : methods) {

			if (method.isConstructor()) {
				List parameters = method.parameters();
				if (parameters.size() == types.size() ) {
					boolean typesEqual = true;
					
					for (int i = 0; i < parameters.size(); i++) {
						SingleVariableDeclaration param = (SingleVariableDeclaration)parameters.get(i);
						assert param != null;
						
						if (!param.getType().toString().equals(types.get(i))) {
							typesEqual = false;
							break;
						}
					}
					
					if (typesEqual) return true;
				}
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
						
						// Check whether the field is already initialized in a constructor in which case we can't
						// initialize it a second time at the declaration point
						if ( !isFieldInitializedInConstructor(fieldDeclType, frag, gd) ) {
	
							// Add initializer
							Expression initializer = rewriteUtil.createDefaultInitializer(fieldDeclType);
							if (initializer == null) continue;
							
							VariableDeclarationFragment newFrag =
									(VariableDeclarationFragment)ASTNode.copySubtree(astRoot, frag);
							newFrag.setInitializer(initializer);
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
