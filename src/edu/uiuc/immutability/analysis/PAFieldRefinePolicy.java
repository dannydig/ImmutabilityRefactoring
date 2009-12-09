/**
 * 
 */
package edu.uiuc.immutability.analysis;

import com.ibm.wala.classLoader.IField;
import com.ibm.wala.demandpa.alg.refinepolicy.FieldRefinePolicy;
import com.ibm.wala.demandpa.alg.statemachine.StateMachine;
import com.ibm.wala.demandpa.flowgraph.IFlowLabel;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;

class PAFieldRefinePolicy implements FieldRefinePolicy {
	  public boolean shouldRefine(IField field, PointerKey basePtr, PointerKey val, IFlowLabel label, StateMachine.State state) {
	    return false;
	  }

	  // override this only if we ever decide to include refinement in out project
	  // probably not useful for the moment
	  public boolean nextPass() {
	    return false;
	  }		
}