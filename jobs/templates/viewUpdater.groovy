// 단순한 접근법: ViewManager를 별도 작업으로 만들지 않고 
// BatchJobTemplate에서 직접 View 업데이트 DSL 스크립트를 생성하는 방식

// ViewUpdater.groovy - 이 파일을 Seed Job에서 마지막에 실행
// 이 스크립트는 모든 작업이 생성된 후 View를 업데이트합니다

import jenkins.model.Jenkins
import hudson.model.ListView

// 작업-View 매핑을 수집합니다
def jobViewMappings = [:]

// Jenkins 인스턴스에서 모든 작업을 가져와서 description에서 View 정보를 추출
def jenkins = Jenkins.getInstance()
jenkins.getAllItems().each { job ->
    def description = job.getDescription()
    
    // Description에서 VIEW_TAG를 찾아서 View 이름 추출
    if (description && description.contains('[VIEW:')) {
        def matcher = description =~ /\[VIEW:(.+?)\]/
        if (matcher.find()) {
            def viewName = matcher.group(1)
            if (!jobViewMappings[viewName]) {
                jobViewMappings[viewName] = []
            }
            jobViewMappings[viewName] << job.getName()
        }
    }
}

// 각 View에 대해 작업 추가
jobViewMappings.each { viewName, jobNames ->
    listView(viewName) {
        description("${viewName} 관련 작업")
        filterBuildQueue()
        filterExecutors()
        
        jobs {
            jobNames.each { jobName ->
                name(jobName)
            }
        }
        
        columns {
            status()
            weather()
            name()
            lastSuccess()
            lastFailure()
            lastDuration()
            buildButton()
        }
    }
    
    println "View '${viewName}'에 ${jobNames.size()}개 작업 추가: ${jobNames.join(', ')}"
}