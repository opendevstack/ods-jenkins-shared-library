package org.ods.orchestration.usecase

import org.ods.orchestration.util.Project
import org.ods.services.BitbucketService
import org.ods.util.IPipelineSteps

import java.text.SimpleDateFormat

@groovy.lang.Grab('com.xlson.groovycsv:groovycsv:1.3')
import com.xlson.groovycsv.CsvParser

class BitbucketTraceabilityUseCase {

    private static final String CSV_FILE = "source-code-review.csv"
    static final String CSV_FOLDER = "review"
    private static final int PAGE_LIMIT = 10

    private final BitbucketService bitbucketService
    private final IPipelineSteps steps
    private final Project project

    BitbucketTraceabilityUseCase(BitbucketService bitbucketService, IPipelineSteps steps, Project project) {
        this.steps = steps
        this.project = project
        this.bitbucketService = bitbucketService
    }

    /**
     * Create a CSV file that contains the following records
     * for every merge event into the integration branch of every ODS component:
     * @return absolutePath of the created file
     */
    @SuppressWarnings(['JavaIoPackageAccess'])
    String generateSourceCodeReviewFile() {
        def file = new File("${steps.env.WORKSPACE}/${CSV_FOLDER}/${CSV_FILE}")
        if (file.exists()) {
            file.delete()
        }
        file.createNewFile()

        def token = bitbucketService.getToken()
        List<Map> repos = getRepositories()
        repos.each {
            processRepo(token, it, file)
        }
        return file.absolutePath
    }


    /**
     * Read an existing csv file and parse the info to obtaining an structured List of data.
     *
     * @param filePath The csv
     * @return List of commits
     */
    @SuppressWarnings(['JavaIoPackageAccess'])
    List<Map> readSourceCodeReviewFile(String filePath) {
        def file = new File(filePath)
        def result = []
        def data = processCsv(file.text, Record.CSV,
            ['commitDate', 'author', 'reviewers', 'pullRequestUrl', 'commitSHA', 'component'])

        for (info in data) {
            def authorInfo = processCsvDeveloper(info.author)
            def reviewers = []
            info.reviewers.split(Record.REVIEWERS_DELIMITER).each {
                def reviewerInfo = processCsvDeveloper(it)
                reviewers << [reviewerName: reviewerInfo.name, reviewerEmail: reviewerInfo.email]
            }

            def commitInfo = [
                date: info.commitDate,
                authorName: authorInfo.name,
                authorEmail: authorInfo.email,
                reviewers: reviewers,
                url: info.pullRequestUrl,
                commit: info.commitSHA,
                component: info.component,
            ]
            result << commitInfo
        }

        return result
    }

    private void processRepo(String token, Map repo, File file) {
        def nextPage = true
        def nextPageStart = 0
        while (nextPage) {
            def commits = bitbucketService.getCommitsForMainBranch(token, repo.repo, PAGE_LIMIT, nextPageStart)
            if (commits.isLastPage) {
                nextPage = false
            } else {
                nextPageStart = commits.nextPageStart
            }
            processCommits(token, repo, commits, file)
        }
    }

    private List<Map> getRepositories() {
        List<Map> result = []
        this.project.getRepositories().each { repository ->
            result << [repo: "${project.data.metadata.id.toLowerCase()}-${repository.id}", branch: repository.branch]
        }
        return result
    }

    private void processCommits(String token, Map repo, Map commits, File file) {
        commits.values.each { commit ->
            Map mergedPR = bitbucketService.getPRforMergedCommit(token, repo.repo, commit.id)
            // Only changes in PR and destiny integration branch
            if (mergedPR.values
                && mergedPR.values[0].toRef.displayId == repo.branch) {
                def record = new Record(getDateWithFormat(commit.committerTimestamp),
                    getAuthor(commit.author),
                    getReviewers(mergedPR.values[0].reviewers),
                    mergedPR.values[0].links.self[(0)].href,
                    commit.id,
                    repo.repo)
                writeCSVRecord(file, record)
            }
        }
    }

    private void writeCSVRecord(File file, Record record) {
        // Jenkins has his own idea how to concatenate Strings
        // Nor '' + '', nor "${}${}", nor StringBuilder nor StringBuffer works properly to
        // get a record entry set in an only String, this is the best approach that works as expected.
        file << record.commitDate
        file << record.CSV
        file << record.author.name
        file << record.author.FIELD_SEPARATOR
        file << record.author.mail
        file << record.CSV
        record.reviewers.each { reviewer ->
            file << reviewer.name
            file << reviewer.FIELD_SEPARATOR
            file << reviewer.mail
            if (reviewer != record.reviewers.last()) {
                file << record.REVIEWERS_DELIMITER
            }
        }
        file << record.CSV
        file << record.mergeRequestURL
        file << record.CSV
        file << record.mergeCommitSHA
        file << record.CSV
        file << record.componentName
        file << record.END_LINE
    }

    private Developer getAuthor(Map author) {
        return new Developer(author.name, author.emailAddress)
    }

    private List getReviewers(List reviewers) {
        List<Developer> approvals = []
        reviewers.each {
            if (it.approved) {
                approvals << new Developer(it.user.name, it.user.emailAddress)
            }
        }

        return approvals
    }

    private String getDateWithFormat(Long timestamp) {
        Date dateObj =  new Date(timestamp)
        return new SimpleDateFormat('yyyy-MM-dd', Locale.getDefault()).format(dateObj)
    }

    private Iterator processCsv(String data, String separator, List<String> columnNames) {
        return new CsvParser().parse(data,
            separator: separator,
            readFirstLine: true,
            columnNames: columnNames, )
    }

    private Object processCsvDeveloper(String data) {
        return processCsv(data, Developer.FIELD_SEPARATOR, ['name', 'email']).next()
    }

    private class Record {

        static final String CSV = ','
        static final String REVIEWERS_DELIMITER = ';'
        static final String END_LINE = '\n'

        String commitDate
        Developer author
        List<Developer> reviewers
        String mergeRequestURL
        String mergeCommitSHA
        String componentName

        @SuppressWarnings(['ParameterCount'])
        Record(String date, Developer author, List<Developer> reviewers, String mergeRequestURL,
               String mergeCommitSHA, String componentName) {
            this.commitDate = date
            this.author = author
            this.reviewers = reviewers
            this.mergeRequestURL = mergeRequestURL
            this.mergeCommitSHA = mergeCommitSHA
            this.componentName = componentName
        }

    }

    private class Developer {

        static final String FIELD_SEPARATOR = '|'
        String name
        String mail

        Developer(String name, String mail) {
            this.name = name
            this.mail = mail
        }

    }

}
