package edu.uiuc.immutability.analysis;

import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;

public class EntrypointData {
	private String className, methodName, methodDescriptor;

	public EntrypointData(String className, String methodName,
			String methodDescriptor) {
		this.className = className;
		this.methodName = methodName;
		this.methodDescriptor = methodDescriptor;
	}

	public String getClassName() {
		return className;
	}

	public String getMethodName() {
		return methodName;
	}

	public String getMethodDescriptor() {
		return methodDescriptor;
	}
	
	/**
	 * 
	 * @param node any node within a MethodDeclaration
	 * @return a new entrypoint
	 */
	public static EntrypointData createEntrypoint(ASTNode node) {
		String className = null;
		String methodName = null;
		String methodDescriptor = null;

		TypeDeclaration typeDeclaration = (TypeDeclaration) ASTNodes.getParent(node, TypeDeclaration.class);
		if (typeDeclaration != null)
			className = "L" + typeDeclaration.resolveBinding().getQualifiedName().replace(".", "/");
		else
			return null;
		
		MethodDeclaration methodDeclaration = (MethodDeclaration) ASTNodes.getParent(node, MethodDeclaration.class);
		if (methodDeclaration != null) {
			methodName = methodDeclaration.getName().getIdentifier();
			
			// TODO: fix this!
			IMethod iMethod = (IMethod) methodDeclaration.resolveBinding().getJavaElement();
			methodDescriptor = "(" + iMethod.getKey().split("\\(")[1].replace(".", "/");
		} else
			return null;

		// add current method to entrypoint list
		return new EntrypointData(className, methodName, methodDescriptor);
	}
	
	public static EntrypointData createEntrypoint(IMethod iMethod) {
		String className = null;
		String methodName = null;
		String methodDescriptor = null;

		IType declaringType = iMethod.getDeclaringType();
		if (declaringType != null)
			className = "L" + declaringType.getFullyQualifiedName().replace(".", "/");
		else
			return null;
		
		methodName = iMethod.getElementName();
			
		methodDescriptor = "(" + iMethod.getKey().split("\\(")[1].replace(".", "/");

		// add current method to entrypoint list
		return new EntrypointData(className, methodName, methodDescriptor);
	}
	
	
	
	public boolean equals(Object obj)
	{
		if(this == obj)
			return true;
		if((obj == null) || (obj.getClass() != this.getClass()))
			return false;
		// object must be Test at this point
		EntrypointData ed = (EntrypointData)obj;

		return className.equals(ed.className)
			&& methodName.equals(ed.methodName)
			&& methodDescriptor.equals(ed.methodDescriptor);
	}

	public int hashCode()
	{
		int hash = 7;
		hash = 31 * hash + className.hashCode();
		hash = 31 * hash + methodName.hashCode();
		hash = 31 * hash + methodDescriptor.hashCode();
		
		return hash;
	}
}
