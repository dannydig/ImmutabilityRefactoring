package edu.uiuc.immutability.ui;

import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IActionDelegate;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;

import edu.uiuc.immutability.MakeImmutableRefactoring;

public class MakeImmutableAction implements IObjectActionDelegate {

	private static final String MAKE_IMMUTABLE_CLASS = "Make Immutable Class";
	private Shell shell;
	private IType targetClass;

	/**
	 * Constructor for Action1.
	 */
	public MakeImmutableAction() {
		super();
	}

	/**
	 * @see IObjectActionDelegate#setActivePart(IAction, IWorkbenchPart)
	 */
	public void setActivePart(IAction action, IWorkbenchPart targetPart) {
		shell = targetPart.getSite().getShell();
	}

	
	/**
	 * @see IActionDelegate#run(IAction)
	 */
	public void run(IAction action) {
		try {
			if (targetClass != null && shell != null && isConvertToParallelArrayAvailable()) {
				MakeImmutableRefactoring refactoring= new MakeImmutableRefactoring(targetClass);
				run(new MakeImmutableWizard(refactoring, MAKE_IMMUTABLE_CLASS), shell, MAKE_IMMUTABLE_CLASS);
			} else
				MessageDialog.openError(shell, "Error MakeImmutableClass", "MakeImmutableClass not applicable for current selection"); 
			
		} catch (JavaModelException e) {
			e.printStackTrace();
		}
	}
	
	public void run(RefactoringWizard wizard, Shell parent, String dialogTitle) {
		try {
			RefactoringWizardOpenOperation operation= new RefactoringWizardOpenOperation(wizard);
			operation.run(parent, dialogTitle);
		} catch (InterruptedException exception) {
			// Do nothing
		}
	}

	/**
	 * @see IActionDelegate#selectionChanged(IAction, ISelection)
	 */
	public void selectionChanged(IAction action, ISelection selection) {
		targetClass= null;
		if (selection instanceof IStructuredSelection) {
			IStructuredSelection extended= (IStructuredSelection) selection;
			Object[] elements= extended.toArray();
			if (elements.length == 1 && elements[0] instanceof IType) {
				targetClass= (IType) elements[0];
			}
		}
//		try {
//			action.setEnabled(isConvertToAtomicIntegerAvailable());
//		} catch (JavaModelException exception) {
//			action.setEnabled(false);
//		}
	}

	private boolean isConvertToParallelArrayAvailable()
			throws JavaModelException {
		return targetClass != null
			&& targetClass.exists()
			&& targetClass.isStructureKnown()
			&& !targetClass.isAnnotation();
	}

}
