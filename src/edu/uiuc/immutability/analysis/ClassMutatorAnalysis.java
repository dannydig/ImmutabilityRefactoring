package edu.uiuc.immutability.analysis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;

import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.cha.ClassHierarchyException;

import edu.uiuc.immutability.analysis.WALAwithConfigurationFileAnalysis;
import edu.uiuc.immutability.analysis.EntrypointData;

public class ClassMutatorAnalysis {
	private final IType targetClass;
	
	Map<MethodDeclaration, MethodSummary> mutators;

	private final IProgressMonitor pm;
	
	public ClassMutatorAnalysis(IType targetClass, IProgressMonitor pm) {
		this.targetClass = targetClass;
		this.pm = pm;
		
		mutators = new HashMap<MethodDeclaration, MethodSummary>();
		
	}
	
	public void findMutators() {
		Set<EntrypointData> entrypoints = new HashSet<EntrypointData>();
		IMethod[] boundaryMethods = findPublicMethods();
		for (IMethod iMethod : boundaryMethods) {
			EntrypointData entrypointData = EntrypointData.createEntrypoint(iMethod);
			if (entrypointData != null)
				entrypoints.add(entrypointData);
		}
		
		pm.setTaskName("finding mutator methods");
		WALAwithConfigurationFileAnalysis analysis;
		try {
			analysis = new WALAwithConfigurationFileAnalysis(targetClass.getJavaProject(), pm);
			analysis.setEntrypoints(entrypoints);
			
			analysis.buildCallGraph();
			CallGraph callGraph = analysis.getCallGraph();
			System.out.println(callGraph);
		} catch (ClassHierarchyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JavaModelException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (CallGraphBuilderCancelException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
			
		
	}
	
	private IMethod[] findPublicMethods() {
		List<IMethod> publicMethods = new ArrayList<IMethod>();
		try {
			IMethod[] allMethods = targetClass.getMethods();
			for (IMethod iMethod : allMethods) {
				if (Flags.isPublic(iMethod.getFlags())) {
					publicMethods.add(iMethod);
				}
			}
		} catch (JavaModelException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return (IMethod[]) publicMethods.toArray(new IMethod[publicMethods.size()]);
	}
	
	public boolean hasMutators() {
		return !mutators.isEmpty();
	}
	
	public List<SimpleName> getFieldAssignments(MethodDeclaration method) {
		return mutators.get(method).getFieldAssignments();
	}
	
	/* TODO: Implement this method! */
	public boolean isMethodAMutator(MethodDeclaration method) {
		return mutators.keySet().contains(method);
	}

	//@Override
//	public boolean visit(MethodDeclaration methodDecl) {
//		if (!methodDecl.isConstructor()) {
//			final MethodSummary methodSummary = new MethodSummary(targetClass);
//			methodDecl.accept(methodSummary);
//			
//			if (methodSummary.hasFieldAssignments()) {
//				mutators.put(methodDecl, methodSummary);
//			}
//		}
//		
//		return false;
//	}
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