package edu.uiuc.immutability;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;

public class FieldWritesVisitor extends ASTVisitor {
	
	private List<IField> results;
	private List<ExpressionStatement> exprStatementsToBeRemoved;
	
	public FieldWritesVisitor() {
		results = new ArrayList<IField>();
		exprStatementsToBeRemoved = new ArrayList<ExpressionStatement>();
	}
	
	public boolean visit(Assignment assignment) {
		Expression leftHandSide = assignment.getLeftHandSide();
		if (leftHandSide instanceof SimpleName) {
			SimpleName variableName = (SimpleName) leftHandSide;
			addField(variableName);
		} else if (leftHandSide instanceof FieldAccess) {
			FieldAccess fieldAccess = (FieldAccess) leftHandSide;
			SimpleName fieldName = fieldAccess.getName();
			addField(fieldName);
		}
		return true;
	}

	@SuppressWarnings("restriction")
	private void addField(SimpleName variableName) {
		IBinding variableBinding = variableName.resolveBinding();
		IJavaElement javaElement = variableBinding.getJavaElement();
		if (javaElement instanceof IField) {
			results.add((IField)javaElement);
			ExpressionStatement exprStatementToBeRemoved = 
					(ExpressionStatement) ASTNodes.getParent(variableName, ExpressionStatement.class);
			exprStatementsToBeRemoved.add(exprStatementToBeRemoved);
		}
	}
	
	public List<IField> getResults(){
		return results;
	}
	
	public List<ExpressionStatement> getExprStatementsToBeRemoved() {
		return exprStatementsToBeRemoved;
	}
}
