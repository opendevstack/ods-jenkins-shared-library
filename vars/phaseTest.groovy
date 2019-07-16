import org.ods.phase.PipelinePhases
import org.ods.util.MultiRepoOrchestrationPipelineUtil

def call(Map metadata, List<Set<Map>> repos) {
    // Get a list of automated test scenarios from Jira
    def issues = jiraGetIssuesForJQLQuery(metadata, "project = ${metadata.id} AND labels = AutomatedTest AND issuetype = sub-task")
        .collect { [ id: it.id, key: it.key, summary: it.fields.summary, description: it.fields.description, url: it.self ]}

    // Execute phase for each repository
    def util = new MultiRepoOrchestrationPipelineUtil(this)
    util.prepareExecutePhaseForReposNamedJob(PipelinePhases.TEST_PHASE, repos)
        .each { group ->
            parallel(group)
        }
}

return this
