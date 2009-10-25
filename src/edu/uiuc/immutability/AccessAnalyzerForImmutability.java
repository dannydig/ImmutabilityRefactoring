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
public class AccessAnalyzerForImmutability extends ASTVisitor {

	private RefactoringStatus status;
	private List<TextEditGroup> groupDescriptions;
	private final MakeImmutableRefactoring refactoring;
	private final ASTRewrite rewriter;
	private final AST astRoot;
	private final ICompilationUnit unit;
	private boolean hasFullConstructor = false;
	
	public AccessAnalyzerForImmutability(
			MakeImmutableRefactoring makeImmutableRefactoring,
			ICompilationUnit unit, ASTRewrite rewriter) {
		this.refactoring = makeImmutableRefactoring;
		this.rewriter = rewriter;
		this.astRoot = rewriter.getAST();
		this.unit = unit;
		status = new RefactoringStatus();
		groupDescriptions = new ArrayList<TextEditGroup>();
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public boolean visit(MethodDeclaration methodDecl) {
		if (doesParentBindToTargetClass (methodDecl) && !methodDecl.isConstructor() ) {
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
				ClassInstanceCreation returnObjectCreation = astRoot.newClassInstanceCreation();
				returnObjectCreation.setType(astRoot.newSimpleType(astRoot.newName(new String[] {classIdentifier})));
				for (ExpressionStatement removedExpressionStatement : fieldWritesVisitor.getRemovedExpressionStatements()) {
					
					Assignment assignment = (Assignment) removedExpressionStatement.getExpression();
					Expression rightHandSide = assignment.getRightHandSide();
					
					while (rightHandSide instanceof Assignment) {
						rightHandSide = ((Assignment)rightHandSide).getRightHandSide();
					}

					returnObjectCreation.arguments().add(ASTNode.copySubtree(astRoot, rightHandSide));
					types.add(rightHandSide.resolveTypeBinding().getName());
				}

				// Lazily create a constructor that initializes all the fields if one does not excist 
				if ( !hasFullConstructor ) {
					if ( !doesClassHaveFullConstructor(declaringClass, types) ) {
						createFullConstructor(declaringClass);
					}
					
					hasFullConstructor = true;
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
	private boolean doesClassHaveFullConstructor(TypeDeclaration classDecl, List<String> types) {
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
	
	@SuppressWarnings("unchecked")
	private void createFullConstructor(TypeDeclaration classDecl) {
		final TextEditGroup editGroup = new TextEditGroup("creating constructor that initializes all the fields of " +
		                                                  "the class to use with setters");
		
		MethodDeclaration constructor = astRoot.newMethodDeclaration();
		SimpleName constructorName = astRoot.newSimpleName(classDecl.getName().getIdentifier());
		constructor.setName(constructorName);
		constructor.setConstructor(true);
		constructor.modifiers().add(astRoot.newModifier(ModifierKeyword.PUBLIC_KEYWORD));
		
		// Create a body block and a formal parameter list to set each field
		Block constructorBody = astRoot.newBlock();
		
		FieldDeclaration[] fields = classDecl.getFields();
		for (FieldDeclaration field : fields) {
			List fragments = field.fragments();
			for (Object fragObject : fragments) {
				VariableDeclarationFragment frag = (VariableDeclarationFragment)fragObject;
				assert frag != null;

				// Add a formal parameter to initialize the field
				SingleVariableDeclaration parameter = astRoot.newSingleVariableDeclaration();
				SimpleName parameterName = (SimpleName) ASTNode.copySubtree(astRoot, frag.getName());
				parameter.setName(parameterName);
				constructor.parameters().add(parameter);
				
				// Initialize the field with the parameter
				SimpleName parameterNameCopy = (SimpleName) ASTNode.copySubtree(astRoot, parameterName);
				ExpressionStatement fieldAssignmentStatement = 
						createFieldAssignmentStatement(field.getType(), frag, parameterNameCopy);
				constructorBody.statements().add(fieldAssignmentStatement);
			}
		}
		
		// Add the body to the constructor
		constructor.setBody(constructorBody);

		addNewConstructorToClass(constructor, classDecl, editGroup);
		
		groupDescriptions.add(editGroup);
	}

	private void addNewConstructorToClass(MethodDeclaration constructor, TypeDeclaration classDecl, final TextEditGroup editGroup) {
		ListRewrite classDeclarationsRewrite = rewriter.getListRewrite(classDecl, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
		
		// Try to insert the new constructor after the last current constructor
		MethodDeclaration lastCurrentConstructor = null;
		MethodDeclaration[] methods = classDecl.getMethods();	
		for(int i = methods.length-1; i >= 0; --i) {
			if (methods[i].isConstructor()) {
				lastCurrentConstructor = methods[i];
			}
		}
		
		if (lastCurrentConstructor != null) {
			classDeclarationsRewrite.insertAfter(constructor, lastCurrentConstructor, editGroup);
		}
		else {
			if (methods.length > 0) {
				// No constructors exist so we insert it before the first method
				classDeclarationsRewrite.insertBefore(constructor, methods[0], editGroup);
			}
			else {
				classDeclarationsRewrite.insertLast(constructor, editGroup);	
			}
		}
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
							Expression initializer = createDefaultInitializer(fieldDeclType);
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
		}
		return false;
	}
	
	private Expression createDefaultInitializer(Type fieldDeclType) {
		Expression initializer = null;
		
		if (fieldDeclType instanceof PrimitiveType) {
			PrimitiveType primType = (PrimitiveType)fieldDeclType;
			
			if (primType.getPrimitiveTypeCode() == PrimitiveType.BOOLEAN) {
				initializer = astRoot.newBooleanLiteral(false);
			}
			else if (primType.getPrimitiveTypeCode() == PrimitiveType.CHAR) {
				CharacterLiteral charLit = astRoot.newCharacterLiteral(); 
				charLit.setCharValue('\u0000');
					
				initializer = charLit;
			}
			else if (primType.getPrimitiveTypeCode() == PrimitiveType.FLOAT) {
				initializer = astRoot.newNumberLiteral("0.0f");
			}
			else if (primType.getPrimitiveTypeCode() == PrimitiveType.DOUBLE) {
				initializer = astRoot.newNumberLiteral("0.0d");
			}
			else if (   primType.getPrimitiveTypeCode() == PrimitiveType.INT
			         || primType.getPrimitiveTypeCode() == PrimitiveType.SHORT
			         || primType.getPrimitiveTypeCode() == PrimitiveType.BYTE) {
		 		initializer = astRoot.newNumberLiteral("0");
			}
			else if (primType.getPrimitiveTypeCode() == PrimitiveType.LONG) {
				initializer = astRoot.newNumberLiteral("0L");
			}
			else {
				// Should never happen
				assert false;
			}
		}
		else if (fieldDeclType instanceof SimpleType) {
			SimpleType simpType = (SimpleType)fieldDeclType;
			assert simpType != null;
			
			initializer = astRoot.newNullLiteral();
		}

		return initializer;
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
					Expression initializer = createDefaultInitializer(fieldDeclType);
					ExpressionStatement assignStmt = createFieldAssignmentStatement(fieldDeclType, frag, initializer);
					
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

	private ExpressionStatement createFieldAssignmentStatement(Type fieldDeclType,
	                                                           VariableDeclarationFragment frag,
	                                                           Expression assignToExpression) {
		Assignment assignment = astRoot.newAssignment();
		//LHS
		FieldAccess fieldAccess = astRoot.newFieldAccess();
		ThisExpression thisExpr = astRoot.newThisExpression();
		fieldAccess.setExpression(thisExpr);
		SimpleName fieldName = astRoot.newSimpleName(frag.getName().toString()); 
		fieldAccess.setName(fieldName);
		assignment.setLeftHandSide(fieldAccess);
		//OP
		assignment.setOperator(Assignment.Operator.ASSIGN);
		//RHS
		assignment.setRightHandSide(assignToExpression);
		
		// Wrap the assignment in an assign statement
		ExpressionStatement assignStmt = astRoot.newExpressionStatement(assignment);
		return assignStmt;
	}

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
	
	@SuppressWarnings("unchecked")
	public Collection getGroupDescriptions() {
		return groupDescriptions;
	}

}
