package org.ods.quickstarter

class CheckoutStage extends Stage {

    protected String STAGE_NAME = 'Checkout quickstarter'

    CheckoutStage(def script, IContext context, Map config = [:]) {
        super(script, context, config)
    }

    def run() {
        script.checkout script.scm
    }

}
