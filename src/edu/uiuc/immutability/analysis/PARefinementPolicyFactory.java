/**
 * 
 */
package edu.uiuc.immutability.analysis;

import com.ibm.wala.demandpa.alg.refinepolicy.AbstractRefinementPolicy;
import com.ibm.wala.demandpa.alg.refinepolicy.RefinementPolicy;
import com.ibm.wala.demandpa.alg.refinepolicy.RefinementPolicyFactory;

class PARefinementPolicyFactory implements RefinementPolicyFactory {
	@Override
	public RefinementPolicy make() {
		class OurRefinementPolicy extends AbstractRefinementPolicy {
			public OurRefinementPolicy() {
				super(new PAFieldRefinePolicy(), new PACallGraphRefinePolicy(),1, new int[] {Integer.MAX_VALUE});
			}
		}
		return new OurRefinementPolicy();
	}	
}