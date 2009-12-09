/**
 * 
 */
package edu.uiuc.immutability.analysis;

import com.ibm.wala.demandpa.alg.refinepolicy.CallGraphRefinePolicy;
import com.ibm.wala.ipa.callgraph.propagation.cfa.CallerSiteContext;

class PACallGraphRefinePolicy implements CallGraphRefinePolicy {
	  public boolean shouldRefine(CallerSiteContext callSiteAndCGNode) {
	    return false;
	  }
	  
	  // override this only if we ever decide to include refinement in out project
	  // probably not useful for the moment
	  public boolean nextPass() {
	    return false;
	  }
}