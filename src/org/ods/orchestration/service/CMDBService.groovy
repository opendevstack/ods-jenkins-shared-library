package org.ods.orchestration.service

import com.cloudbees.groovy.cps.NonCPS
import org.ods.util.ILogger

import groovy.json.JsonSlurper

@SuppressWarnings(['LineLength', 'ParameterName'])
class CMDBService {
    // FIXME
    private static final String CLIENT_ID = "656b77d30d694a57a5dfe800d5dc6298"
    private static final String CLIENT_SECRET = "sj)P.AIN.|"
    private static final String INSTANCE = "boehringereval.service-now.com"
    private static final String USERNAME = "x2sysdesign"
    private static final String PASSWORD = "Drop_34500x!2User"

    class HTTPUtil {
        private String accessToken

        HTTPUtil() {
            this.accessToken = getAccessToken()
        }

        @NonCPS
        private String getAccessToken() {
            def response = this.post("https://${CMDBService.this.INSTANCE}/oauth_token.do",
                "grant_type=password&client_id=${CMDBService.this.CLIENT_ID}&client_secret=${CMDBService.this.CLIENT_SECRET}&username=${CMDBService.this.USERNAME}&password=${CMDBService.this.PASSWORD}",
                "application/x-www-form-urlencoded"
            )

            return response.access_token
        }

        @NonCPS
        def get(String url) {
            def connection = new URL(url).openConnection()
            connection.setAllowUserInteraction(false)
            connection.setRequestProperty("Authorization", "Bearer ${this.accessToken}")
            connection.setRequestProperty("Accept", "application/json")
            return new JsonSlurper().parse(connection.inputStream)
        }

        @NonCPS
        def post(String url, String body, String contentType) {
            def connection = new URL(url).openConnection() as HttpURLConnection
            connection.setAllowUserInteraction(false)
            connection.setRequestMethod("POST")
            connection.setDoOutput(true)
            connection.setRequestProperty("Content-Type", contentType)
            connection.outputStream.withWriter { writer -> writer << body }
            return new JsonSlurper().parse(connection.inputStream)
        }
    }

    private static final int MAX_DEPTH = 5
    private static final List SYSPARM_CI_FIELDS = ["sys_id", "sys_class_name", "name", "u_name_business_friendly", "application_type", "install_type", "short_description", "owned_by", "managed_by", "u_gxp_relevant", "u_gxp_criticality", "u_slc_documents_location_items_and_con", "u_system_design_specification_link", "u_validation_determination_reference", "u_gamp_category", "service_classification"]
    private static final int SYSPARM_CI_RELATIONS_LIMIT = 1000
    private static final List SYSPARM_CMDB_CI_FIELDS = ["sys_class_name"]
    private static final List SYSPARM_CMDB_REL_CI_FIELDS = ["parent,child,type"]
    private static final List SYSPARM_CMDB_REL_TYPE_FIELDS = ["name"]

    private final HTTPUtil httpUtil
    private final ILogger logger

    CMDBService(ILogger logger) {
        this.httpUtil = new HTTPUtil()
        this.logger = logger
    }

    @NonCPS
    private String composeDownstreamCiRelationsIdsUrl(String sysId) {
        def sysparm_query = "parent=${sysId}"
        return "https://${this.INSTANCE}/api/now/table/cmdb_rel_ci?sysparm_query=${sysparm_query}&sysparm_fields=${this.SYSPARM_CMDB_REL_CI_FIELDS.join(',')}&sysparm_limit=${this.SYSPARM_CI_RELATIONS_LIMIT}"
    }

    @NonCPS
    private String composeParentCiDataUrl(String ciName) {
        def sysparm_query = "name=${ciName}"
        return "https://${this.INSTANCE}/api/now/table/cmdb_ci_business_app?sysparm_query=${sysparm_query}&sysparm_fields=${this.SYSPARM_CI_FIELDS.join(',')}&sysparm_limit=1"
    }

    @NonCPS
    private String composeRelationalCiDataUrl(String sysId, String sysClassName) {
        def sysparm_query = "sys_id=${sysId}"
        return "https://${this.INSTANCE}/api/now/table/${sysClassName}?sysparm_query=${sysparm_query}&sysparm_fields=${this.SYSPARM_CI_FIELDS.join(',')}&sysparm_limit=1"
    }

    @NonCPS
    private String composeRelationTypeUrl(String relationTypeId) {
        def sysparm_query = "sys_id=${relationTypeId}"
        return "https://${this.INSTANCE}/api/now/table/cmdb_rel_type?sysparm_query=${sysparm_query}&sysparm_fields=${this.SYSPARM_CMDB_REL_TYPE_FIELDS.join(',')}&sysparm_limit=1"
    }

    @NonCPS
    private String composeSysClassIdUrl(String sysId) {
        def sysparm_query = "sys_id=${sysId}"
        return "https://${this.INSTANCE}/api/now/table/cmdb_ci?sysparm_query=${sysparm_query}&sysparm_fields=${this.SYSPARM_CMDB_CI_FIELDS.join(',')}&sysparm_limit=1"
    }

    @NonCPS
    private String composeUpstreamCiRelationsIdsUrl(String sysId) {
        def sysparm_query = "child=${sysId}"
        return "https://${this.INSTANCE}/api/now/table/cmdb_rel_ci?sysparm_query=${sysparm_query}&sysparm_fields=${this.SYSPARM_CMDB_REL_CI_FIELDS.join(',')}&sysparm_limit=${this.SYSPARM_CI_RELATIONS_LIMIT}"
    }

    @NonCPS
    private List loadDownstreamCiRelationsIds(String parentSysId) {
        def downstreamCiRelationsIdsResponse = this.httpUtil.get(
            this.composeDownstreamCiRelationsIdsUrl(parentSysId)
        )

        return downstreamCiRelationsIdsResponse.result
    }

    @NonCPS
    private List loadUpstreamCiRelationsIds(String parentSysId) {
        def upstreamCiRelationsIdsResponse = this.httpUtil.get(
            this.composeUpstreamCiRelationsIdsUrl(parentSysId)
        )

        // Swap parent and child ids since upstream relations come swapped
        upstreamCiRelationsIdsResponse.result.each { ids ->
            def child = ids.child
            ids.child = ids.parent
            ids.parent = child
        }

        return upstreamCiRelationsIdsResponse.result
    }

    @NonCPS
    private void loadExtraCiProperties(Map node) {
        if (node.managed_by) {
            println "Node has managed_by: $node"
            // Resolve managed_by email CI param
            def response = this.httpUtil.get("${node.managed_by.link}?sysparm_fields=email")
            node.managed_by = response.result.email
        }

        if (node.owned_by) {
            println "Node has owned_by: $node"
            // Resolve owned_by email CI param
            def response = this.httpUtil.get("${node.owned_by.link}?sysparm_fields=email")
            node.owned_by = response.result.email
        }
    }

    @NonCPS
    public Map loadParentCiData(String ciName) {
        def response = this.httpUtil.get(
            this.composeParentCiDataUrl(ciName)
        )

        def node = [:]
        if (response.result) {
            node = response.result.first()
            this.sanitizeCiProperties(node)
            this.loadExtraCiProperties(node)
        }

        return node
    }

    @NonCPS
    private Map loadRelationalCiData(String childSysId, String sysClassName) {
        def response = this.httpUtil.get(
            this.composeRelationalCiDataUrl(childSysId, sysClassName)
        )

        return response.result.first()
    }

    @NonCPS
    private void loadRelationalCisData(Map parentNode, Closure ciRelationsLookupStrategy, Closure relationSanitizerStrategy, List parentNodeIds = [], int depth = 0, int maxDepth = MAX_DEPTH) {
        if (depth >= maxDepth) {
            return
        }

        // Break cycles
        if (parentNodeIds.contains(parentNode.sys_id)) {
            return
        }

        if (!parentNode.children) {
            parentNode.children = []
        }

        parentNodeIds << parentNode.sys_id

        def ciRelationsIds = ciRelationsLookupStrategy(parentNode.sys_id)
        for (Map item in ciRelationsIds) {
            def childSysId = item.child.value
            def relationTypeId = item.type.value

            // Skip processing links to self
            if (parentNode.sys_id == childSysId) {
                break
            }

            def relationType = this.loadRelationType(relationTypeId)
            // if (!(relationType.name ==~ /(?i)members.*/)) {
                def childSysClass = this.loadSysClass(childSysId)
                if (childSysClass) {
                    def childNode = this.loadRelationalCiData(childSysId, childSysClass.sys_class_name)
                    childNode.relation = relationType
                    this.sanitizeCiProperties(childNode, parentNode, relationSanitizerStrategy)
                    this.loadExtraCiProperties(childNode)
                    parentNode.children << childNode

                    if (!(childSysClass.sys_class_name ==~ /(?i)exception|software|branch|leaf|server|database|business_app|service/)) {
                        loadRelationalCisData(childNode, ciRelationsLookupStrategy, relationSanitizerStrategy, parentNodeIds, depth + 1, maxDepth)
                    } else {
                        println "No further relationships looked up for this class of CI\n"
                    }
                }
            // }
        }
    }

    @NonCPS
    private Map loadRelationType(String relationTypeId) {
        def response = this.httpUtil.get(
            this.composeRelationTypeUrl(relationTypeId)
        )

        return response.result.first()
    }

    @NonCPS
    private Map loadSysClass(String sysId) {
        def response = this.httpUtil.get(
            this.composeSysClassIdUrl(sysId)
        )

        return response.result ? response.result.first() : [:]
    }

    @NonCPS
    public Map loadData(String ciName) {
        def parent = this.loadDownstreamData(ciName)
        parent.children.addAll(this.loadUpstreamData(ciName).children)
        return parent
    }

    @NonCPS
    public Map loadDownstreamData(String ciName) {
        def parent = this.loadParentCiData(ciName)

        Closure downstreamCiRelationsLookupStrategy = { String parentSysId ->
            return this.loadDownstreamCiRelationsIds(parentSysId)
        }

        Closure downstreamRelationSanitizerStrategy = { Map relation ->
            def result = [:]
            result.name = relation.name.split("::").first()
            result.isDownstream = true
            return result
        }

        this.loadRelationalCisData(
            parent,
            downstreamCiRelationsLookupStrategy,
            downstreamRelationSanitizerStrategy
        )

        return parent
    }

    @NonCPS
    public Map loadUpstreamData(String ciName) {
        def parent = this.loadParentCiData(ciName)

        Closure upstreamCiRelationsLookupStrategy = { String parentSysId ->
            return this.loadUpstreamCiRelationsIds(parentSysId)
        }

        Closure upstreamRelationSanitizerStrategy = { Map relation ->
            def result = [:]
            result.name = relation.name.split("::").last()
            result.isDownstream = false
            return result
        }

        this.loadRelationalCisData(
            parent,
            upstreamCiRelationsLookupStrategy,
            upstreamRelationSanitizerStrategy
        )

        return parent
    }

    @NonCPS
    private void sanitizeCiProperties(Map node, Map parentNode = null, Closure relationSanitizerStrategy = null) {
        if (parentNode) {
            node.parent_sys_id = parentNode.sys_id
            node.parent_name = parentNode.name
        }

        if (relationSanitizerStrategy) {
            node.relation = relationSanitizerStrategy(node.relation)
        }
    }
}
