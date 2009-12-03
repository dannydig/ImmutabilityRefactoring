package edu.uiuc.immutability.analysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;

public class ClassMutatorAnalysis extends ASTVisitor {
	private final IType targetClass;
	
	Map<MethodDeclaration, MethodSummary> mutators;
	
	public ClassMutatorAnalysis(IType targetClass) {
		this.targetClass = targetClass;
		
		mutators = new HashMap<MethodDeclaration, MethodSummary>();
	}
	
	public boolean hasMutators() {
		return !mutators.isEmpty();
	}
	
	public List<SimpleName> getFieldAssignments(MethodDeclaration method) {
		return mutators.get(method).getFieldAssignments();
	}

	@Override
	public boolean visit(MethodDeclaration methodDecl) {
		if (!methodDecl.isConstructor()) {
			final MethodSummary methodSummary = new MethodSummary(targetClass);
			methodDecl.accept(methodSummary);
			
			if (methodSummary.hasFieldAssignments()) {
				mutators.put(methodDecl, methodSummary);
			}
		}
		
		return false;
	}
}


/* Utility Visitors */

/**
 * Summarizes the field writes in a method 
 */
class MethodSummary extends ASTVisitor {
	private final IType targetClass;
	private List<SimpleName> fieldAssignments;

	public MethodSummary(IType targetClass) {
		this.targetClass = targetClass;
		fieldAssignments = new ArrayList<SimpleName>();
	}
	
	public List<SimpleName> getFieldAssignments() {
		return fieldAssignments;
	}

	public boolean hasFieldAssignments() {
		return !fieldAssignments.isEmpty();
	}

	public boolean visit(Assignment assignment) {
		SimpleName fieldName = null;

		Expression leftHandSide = assignment.getLeftHandSide();
		if (leftHandSide instanceof SimpleName ) {
			SimpleName possibleField = (SimpleName) leftHandSide;
			IJavaElement possibleFieldParent = possibleField.resolveBinding().getJavaElement().getParent();
			
			if ( possibleFieldParent.getHandleIdentifier().equals(targetClass.getHandleIdentifier()) ) {
				fieldName = possibleField;
			}
		} else if (leftHandSide instanceof FieldAccess) {
			FieldAccess fieldAccess = (FieldAccess) leftHandSide;
			fieldName = fieldAccess.getName();
		}
		
		if (fieldName != null) {
			fieldAssignments.add(fieldName);
		}
		
		// True because there may be a chain of assignments: i = j = 0;
		return true;
	}
}