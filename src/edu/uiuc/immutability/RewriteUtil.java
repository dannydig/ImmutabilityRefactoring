package edu.uiuc.immutability;

import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
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
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.Modifier.ModifierKeyword;

public class RewriteUtil {
	
	private final String NEW_THIS_NAME = "_this";
	
	private final AST astRoot;
	
	public RewriteUtil(AST astRoot) {
		this.astRoot = astRoot;
	}
	
	public SimpleName getThisSimpleName() {
		return astRoot.newSimpleName(NEW_THIS_NAME);
	}

	public Expression createDefaultInitializer(Type fieldDeclType) {
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
	
	public ExpressionStatement createFieldAssignmentStatement(Type fieldDeclType,
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
	
	@SuppressWarnings("unchecked")
	public MethodDeclaration createFullConstructor(TypeDeclaration classDecl) {
		MethodDeclaration constructor = createEmptyConstructor(classDecl);
		
		Block constructorBody = constructor.getBody();
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
				Expression parameterNameCopy = (Expression) ASTNode.copySubtree(astRoot, parameterName);
				ExpressionStatement fieldAssignmentStatement = 
						createFieldAssignmentStatement(field.getType(), frag, parameterNameCopy);
				constructorBody.statements().add(fieldAssignmentStatement);
			}
		}

		return constructor;
	}
	
	@SuppressWarnings("unchecked")
	public MethodDeclaration createDefaultConstructor(TypeDeclaration classDecl) {
		MethodDeclaration constructor = createEmptyConstructor(classDecl);
		
		Block constructorBody = constructor.getBody();
		FieldDeclaration[] fields = classDecl.getFields();
		for (FieldDeclaration field : fields) {
			List fragments = field.fragments();
			for (Object fragObject : fragments) {
				VariableDeclarationFragment frag = (VariableDeclarationFragment)fragObject;
				assert frag != null;

				Expression initializer = (frag.getInitializer() != null) ? frag.getInitializer()
				                                                         : createDefaultInitializer(field.getType());
				
				// Initialize the field with the parameter
				Expression initializerCopy = (Expression) ASTNode.copySubtree(astRoot, initializer);
				ExpressionStatement fieldAssignmentStatement = 
						createFieldAssignmentStatement(field.getType(), frag, initializerCopy);
				constructorBody.statements().add(fieldAssignmentStatement);
			}
		}

		return constructor;
	}

	@SuppressWarnings("unchecked")
	public Expression createMutateExpression(SimpleType classType, List<Expression> arguments) {
		SimpleType classTypeCopy = (SimpleType) ASTNode.copySubtree(astRoot, classType);
		ClassInstanceCreation newObject = astRoot.newClassInstanceCreation();
		newObject.setType(classTypeCopy);
		
		for (Expression expression : arguments) {
			newObject.arguments().add(expression);
		}
		
		Assignment mutationAssignment = astRoot.newAssignment();
		mutationAssignment.setLeftHandSide((Expression) ASTNode.copySubtree(astRoot, getThisSimpleName()));
		mutationAssignment.setOperator(Assignment.Operator.ASSIGN);
		mutationAssignment.setRightHandSide(newObject);
		
		return mutationAssignment;
	}
	
	@SuppressWarnings("unchecked")
	private MethodDeclaration createEmptyConstructor(TypeDeclaration classDecl) {
		MethodDeclaration constructor = astRoot.newMethodDeclaration();
		SimpleName constructorName = astRoot.newSimpleName(classDecl.getName().getIdentifier());
		constructor.setName(constructorName);
		constructor.setConstructor(true);
		constructor.modifiers().add(astRoot.newModifier(ModifierKeyword.PUBLIC_KEYWORD));
		
		// Create a body block and a formal parameter list to set each field
		Block constructorBody = astRoot.newBlock();
		constructor.setBody(constructorBody);
		return constructor;
	}
}
