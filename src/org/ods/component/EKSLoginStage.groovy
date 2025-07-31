package org.ods.component

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.ods.util.ILogger

@SuppressWarnings('ParameterCount')
@TypeChecked
class EKSLoginStage extends Stage {

    public final String STAGE_NAME = 'EKS Login'
    private final Options options

    @TypeChecked(TypeCheckingMode.SKIP)
    EKSLoginStage(
        def script,
        IContext context,
        Map<String, Object> config,       
        ILogger logger) {
        super(script, context, logger)
        this.options = new Options(config) 
    }
    
    // This is called from Stage#execute
    @SuppressWarnings(['AbcMetric'])
    @TypeChecked(TypeCheckingMode.SKIP)
    protected run() {
         int status = steps.sh(
            script: "aws eks update-kubeconfig --region $AWS_REGION --name $EKS_CLUSTER_NAME",
            returnStatus: true,
            label: "Login to -region $AWS_REGION --name $EKS_CLUSTER_NAME"
        ) as int
        if (status != 0) {
            script.error("Could not Login to -region $AWS_REGION --name $EKS_CLUSTER_NAME, status ${status}")
        }      
    }
}
