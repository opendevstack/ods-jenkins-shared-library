package org.ods.orchestration.usecase

import com.cloudbees.groovy.cps.NonCPS
import org.ods.orchestration.service.CMDBService
import org.ods.util.ILogger

@SuppressWarnings(['LineLength', 'ParameterName'])
class CMDBUseCase {
    private final CMDBService cmdb
    private final ILogger logger

    CMDBUseCase(CMDBService cmdb, ILogger logger) {
        this.cmdb = cmdb
        this.logger = logger
    }

    @NonCPS
    public static List<Map> findEnvironments(Map rootNode) {
        def result = []

        def findDevEnv = { node ->
            node.children.findAll { child ->
                return this.isDevelopmentEnvironment(child)
            }
        }

        def findAppServicesInDevEnv = { node ->
            node.children.findAll { child ->
                return this.isDevelopmentEnvironment(node) \
                    && this.isApplicationService(child)
            }
        }

        def findDatabaseProjects = { node ->
            node.children.findAll { child ->
                return this.isDatabaseCatalogNode(child)
            }
        }

        def findDatabases = { node ->
            node.children.findAll { child ->
                return this.isDatabaseNode(child)
            }
        }

        def findServers = { node ->
            node.children.findAll { child ->
                return this.isServerNode(child)
            }
        }

        findDevEnv(rootNode).each { devEnv ->
            def devEnvClone = devEnv.clone()
            devEnvClone.children = []
            result << devEnvClone

            findAppServicesInDevEnv(devEnv).each { appService ->
                def appServiceClone = appService.clone()
                appServiceClone.children = []
                devEnvClone.children << appServiceClone
            }

            findDatabaseProjects(devEnv).each { dbProject ->
                def dbProjectClone = dbProject.clone()
                dbProjectClone.children = []
                devEnvClone.children << dbProjectClone

                findDatabases(dbProject).each { db ->
                    def dbClone = db.clone()
                    dbClone.children = []
                    dbProjectClone.children << dbClone

                    findServers(db).each { server ->
                        def serverClone = server.clone()
                        serverClone.children = []
                        dbClone.children << serverClone
                    }
                }
            }
        }

        return result
    }

    @NonCPS
    public static List<Map> findInterfaces(Map rootNode, ILogger logger) {
        def result = []

        def findInterfaces_ = { node ->
            node.children.findAll { child ->
                return isInterface(child)
            }
        }

        def findAPIs = { node ->
            node.children.findAll { child ->
                return isAPI(child)
            }
        }
      
        def findInformationObjects = { node ->
            node.children.findAll { child ->
                return isInformationObject(child)
            }
        }

        def findInterfaceInstalledSystems = { node ->
            node.children.findAll { child ->
                return isInterfaceInstalledSystem(child, logger)
            }
        }

        findInterfaces_(rootNode).each { interface_ ->
            def interfaceClone = interface_.clone()
            interfaceClone.children = []
            result << interfaceClone

            findInterfaceInstalledSystems(interface_).each { sys ->
                def sysClone = sys.clone()
                sysClone.children = []
                interfaceClone.children << sysClone
            }

            findInformationObjects(interface_).each { info ->
                def infoClone = info.clone()
                infoClone.children = []
                interfaceClone.children << infoClone
            }

            findAPIs(interface_).each { api ->
                def apiClone = api.clone()
                apiClone.children = []
                interfaceClone.children << apiClone
            }
        }

        return result
    }

    @NonCPS
    public static List<Map> findModules(Map rootNode) {
        def result = []

        def findModules = { node ->
            node.children.findAll { child ->
                return isModule(child)
            }
        }

        findModules(rootNode).each { module ->
            def moduleClone = module.clone()
            moduleClone.children = []
            result << moduleClone
        }

        return result
    }

    @NonCPS
    public static boolean isAPI(Map node) {
        return node.application_type == "Application Programming Interface"
    }

    @NonCPS
    public static boolean isApplicationService(Map node) {
        return node.sys_class_name == "cmdb_ci_service" \
            && (node.service_classification == "Application Service" \
                || node.service_classification == "Infrastructure Service") \
            && node.name.startsWith("BS_")
    }

    @NonCPS
    public static boolean isDatabaseCatalogNode(Map node) {
        return node.sys_class_name == "cmdb_ci_db_catalog"
    }

    @NonCPS
    public static boolean isDatabaseNode(Map node) {
        return node.sys_class_name == "cmdb_ci_database"
    }

    @NonCPS
    public static boolean isDevelopmentEnvironment(Map node) {
        return this.isEnvironment(node) \
            && node.name.endsWith("(DEVELOPMENT)")
    }

    @NonCPS
    public static boolean isEnvironment(Map node) {
        return node.sys_class_name == "cmdb_ci_service" \
            && node.service_classification == "Application Service" \
            && node.name.startsWith("BS_") \
            && (node.name.endsWith("(DEVELOPMENT)") \
                || node.name.endsWith("(QUALITYASSURANCE)") \
                || node.name.endsWith("(PRODUCTION)")
            )
    }

    @NonCPS
    public static boolean isInterface(Map node) {
        return node.name.contains("-IF-")
    }

    @NonCPS
    public static boolean isInformationObject(Map node) {
        return node.sys_class_name == "u_cmdb_ci_information_object"
    }

    @NonCPS
    public static boolean isInterfaceInstalledSystem(Map node, ILogger logger) {
        logger.debug "CMDBUseCase::isInterfaceInstalledSystem: node: ${node}"
        logger.debug "CMDBUseCase::isInterfaceInstalledSystem: node.parent_name.contains(IF): ${node.parent_name.contains('-IF-')}"
        logger.debug "CMDBUseCase::isInterfaceInstalledSystem: node.relation.name.toLowerCase().startsWith('installed on'): ${node.relation.name.toLowerCase().startsWith('installed on')}"
        return node.parent_name.contains("-IF-") \
            && node.relation.name.toLowerCase().startsWith("installed on")
    }

    @NonCPS
    public static boolean isModule(Map node) {
        return node.application_type == "Module"
    }

    @NonCPS
    public static boolean isServerNode(Map node) {
        return node.sys_class_name == "cmdb_ci_server"
    }

    @NonCPS
    public Map loadData(String ciName) {
        return this.cmdb.loadData(ciName)
    }

    @NonCPS
    public List toFlatData(Map node, Closure nodeSanitizerStrategy = this.&defaultNodeSanitizerStrategy, List result = [], Map parentNode = null) {
        def item = node.clone()
        item.remove('children')
        if (nodeSanitizerStrategy) {
           nodeSanitizerStrategy(item)
        }
        result << item

        node.children?.each { Map childNode ->
            toFlatData(childNode, nodeSanitizerStrategy, result, item)
        }

        return result
    }

    /*
    @NonCPS
    public String toMarkdownTableData(Map rootNode) {
        def sanitizeMarkdownTableString = { String value ->
            // Escape Markdown pipe characters inside table
            value = value.replaceAll(/\|/, "\\\\|")
            return value
        }

        def computeTableRow = { def key, def value, StringBuilder result ->
            result << "| $key | $value |\n"
        }

        def computeTableHeader = { StringBuilder result ->
            computeTableRow("Property", "Value", result)
            result << "| --- |--- |\n"
        }

        def computeTableBody = { Map node, StringBuilder result ->
            computeTableRow("Name", sanitizeMarkdownTableString(node.name), result)
            if (node.u_name_business_friendly && node.u_name_business_friendly != node.name) computeTableRow("Friendly Name", sanitizeMarkdownTableString(node.u_name_business_friendly), result)
            if (node.relation) {
                computeTableRow("Relation", sanitizeMarkdownTableString(node.relation.friendly_name), result)
            }
            if (node.short_description) computeTableRow("Description", sanitizeMarkdownTableString(node.short_description).replaceAll("(\\n)+", " "), result)
            if (node.application_type) computeTableRow("Type", sanitizeMarkdownTableString(node.application_type), result)
            if (node.u_gxp_relevant) computeTableRow("GxP Relevant", sanitizeMarkdownTableString(node.u_gxp_relevant), result)
            if (node.u_gxp_criticality) computeTableRow("GxP Criticality", sanitizeMarkdownTableString(node.u_gxp_criticality), result)
            if (node.u_gamp_category) computeTableRow("GAMP Category", sanitizeMarkdownTableString(node.u_gamp_category), result)
            if (node.owned_by) computeTableRow("System Owner", sanitizeMarkdownTableString(node.owned_by), result)
            if (node.managed_by) computeTableRow("System Lead", sanitizeMarkdownTableString(node.managed_by), result)
            if (node.u_validation_determination_reference) computeTableRow("Validation Determination", sanitizeMarkdownTableString(node.u_validation_determination_reference), result)
            if (node.u_slc_documents_location_items_and_con) computeTableRow("SLC Documents Location", sanitizeMarkdownTableString(node.u_slc_documents_location_items_and_con), result)
        }

        def result = new StringBuilder()

        def flatData = this.toFlatData(rootNode, this.&getNodeSanitizerStrategy)
        flatData.each { node ->
            computeTableHeader(result)
            computeTableBody(node, result)
            result << "\n"
        }

        return result.toString()
    }
    */

    @NonCPS
    public String toMermaidGraphCode(Map rootNode) {
        if (!rootNode) return ""

        def entities = [] as Set
        def relations = [] as Set

        def nodeSanitizerStrategy = { Map node ->
            defaultNodeSanitizerStrategy(node)

            if (node.relation) {
                if (this.isAPI(node) || this.isEnvironment(node) || this.isInterface(node) || this.isModule(node)) {
                    node.relation.friendly_name = null
                }
            }

            if (this.isAPI(node)) {
                node.friendly_name = "${node.name}<br/>(API)"
            } else if (this.isApplicationService(node) && !this.isEnvironment(node)) {
                node.friendly_name = "${node.name}<br/>(${node.service_classification})"
            } else if (this.isEnvironment(node)) {
                node.friendly_name = "${node.name}<br/>(Environment)"
            } else if (this.isInterface(node)) {
                node.friendly_name = "${node.name}<br/>(Interface)"
            } else if (this.isModule(node)) {
                node.friendly_name = "${node.name}<br/>(Module)"
            } else if (this.isDatabaseCatalogNode(node)) {
                node.friendly_name = "${node.name}<br/>(Database Catalog)"
            } else if (this.isDatabaseNode(node)) {
                node.friendly_name = "${node.name}<br/>(Database)"
            }
        }

        def sanitizeEdgeText = { String text ->
            return text.replaceAll(/(?i).*uses.*/, "uses")
        }

        def sanitizeNodeId = { String id ->
            return id.replaceAll("[()]", "-").replaceAll("--", "-")
        }

        def sanitizeNodeText = { String text ->
            return text.replaceAll("[(]", "#40;").replaceAll("[)]", "#41;").replaceAll("--", "-")
        }

        def toEdgeCode = { Map node ->
            if (node.relation.friendly_name) {
                return "    ${sanitizeNodeId(node.parent_name)} -->|\"${sanitizeEdgeText(node.relation.friendly_name)}\"| ${sanitizeNodeId(node.name)}"
            } else {
                return "    ${sanitizeNodeId(node.parent_name)} --> ${sanitizeNodeId(node.name)}"
            }
        }

        def toNodeCode = { Map node ->
            def id = sanitizeNodeId(node.name)
            def text = sanitizeNodeText(node.friendly_name ?: node.name)
            return "    ${id}(${text})"
        }

        def flatData = this.toFlatData(rootNode, nodeSanitizerStrategy)
        flatData.each { node ->
            entities << toNodeCode(node)
            if (node.relation && node.parent_name) relations << toEdgeCode(node)
        }

        def result = new StringBuilder()
        result << "graph TB\n"
        entities.each { result << it << "\n" }
        relations.each { result << it << "\n" }
        return result.toString()
    }

    @NonCPS
    private static void defaultNodeSanitizerStrategy(Map node) {
        def sanitizeValue = { String value ->
            value = value ?: ""
            value = value.replaceAll(/^true$/, "Yes")
            value = value.replaceAll(/^false$/, "No")
            return value
        }

        def sanitizeRelationText = { String text ->
            return text.replaceAll(/(?i).*uses.*/, "uses")
        }

        def computeNodeRelationFriendlyName = {
            def result = ""

            if (it.parent_name) {
                if (this.isEnvironment(it)) {
                    result = "${it.name} is an environment of ${it.parent_name}"
                } else if (this.isModule(it)) {
                    result = "${it.name} is a module of ${it.parent_name}"
                } else {
                    result = "${it.parent_name} ${sanitizeRelationText(it.relation.name.toLowerCase())} ${it.name}"
                }
            }

            return result
        }

        if (node.u_name_business_friendly && node.u_name_business_friendly != node.name) node.name_business_friendly = node.u_name_business_friendly
        if (node.relation) node.relation.friendly_name = computeNodeRelationFriendlyName(node)
        if (node.short_description) node.short_description = node.short_description.replaceAll("(\\n)+", " ")
        if (node.u_gxp_relevant) node.gxp_relevant = sanitizeValue(node.u_gxp_relevant)
        if (node.u_gxp_criticality) node.gxp_criticality = node.u_gxp_criticality
        if (node.u_gamp_category) node.gamp_category = node.u_gamp_category
        if (node.u_validation_determination_reference) node.validation_determination_reference = node.u_validation_determination_reference
        if (node.u_slc_documents_location_items_and_con) node.slc_documents_location = node.u_slc_documents_location_items_and_con
    }
}
