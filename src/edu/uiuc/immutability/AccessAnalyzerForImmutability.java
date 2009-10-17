package edu.uiuc.immutability;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.Modifier.ModifierKeyword;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.dom.ModifierRewrite;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.text.edits.TextEditGroup;

public class AccessAnalyzerForImmutability extends ASTVisitor {

	private RefactoringStatus status;
	private List<TextEditGroup> groupDescriptions, blah;
	private final MakeImmutableRefactoring refactoring;
	private final ASTRewrite rewriter;
	private final AST astRoot;
	
	public AccessAnalyzerForImmutability(
			MakeImmutableRefactoring makeImmutableRefactoring,
			ICompilationUnit unit, ASTRewrite rewriter) {
		this.refactoring = makeImmutableRefactoring;
		this.rewriter = rewriter;
		this.astRoot = rewriter.getAST();
		status = new RefactoringStatus();
		groupDescriptions = new ArrayList<TextEditGroup>();
	}
	
	@Override
	public boolean visit(FieldDeclaration fieldDecl) {
		if (doesParentBindToTargetClass(fieldDecl)) {
			List modifiers = fieldDecl.modifiers();
			if (!Flags.isFinal(fieldDecl.getModifiers())) {
				int finalModifiers = fieldDecl.getModifiers() | ModifierKeyword.FINAL_KEYWORD.toFlagValue();
				TextEditGroup gd = new TextEditGroup("change to final");
				ModifierRewrite.create(rewriter, fieldDecl).setModifiers(finalModifiers, gd);
				groupDescriptions.add(gd);
			}
		}
		return false;
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
	
	public Collection getGroupDescriptions() {
		return groupDescriptions;
	}

}

class A {
	int i = 10, j = 12;
}
