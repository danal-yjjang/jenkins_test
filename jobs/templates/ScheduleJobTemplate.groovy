def job (dslFactory, Map config) {


  def pipelineScript = """
pipeline {
    agent { node { label 'ScheduleNode' } }

    environment {
        DATE = ${getFormatterDate()}
    }
    stages {
${config.stages ? config.stages.collect { stage ->
    return """        stage('${stage.name}') {
            steps {
                script {
                    listGenerate(${stage.values ? stage.values.collect {  it == 'DATE' ? "${it}" : "'${it}'" }.join(', ') : ''})    
                }
            }
        }"""
}.join('\n') : ''}
    }
}

def listGenerate(${config.parameters ? config.parameters.collect {param -> 
    return "String ${param}"
}.join(', ') : ''}) {
    catchError(buildResult: 'FAILURE', stageResult: 'FAILURE'){
        def params = []
        ${config.parameters ? config.parameters.collect { param -> 
            "params.add(string(name:'${param}', value: ${param}))"
        }.join('\n        ') : ''}
        build job: '${config.targetJobName}', parameters: params
    }
}
  """

  def job = dslFactory.pipelineJob(config.name) {
    description(config.description ?: config.name)

    // 기존 Job이 비활성화되어 있다면 그 상태 유지
    def existingJob = jenkins.model.Jenkins.instance.getItem(config.name)
    if (existingJob && existingJob.isDisabled()) {
        disabled(true)
    }

    // 오래된 빌드 삭제 설정
    logRotator {
      daysToKeep(3)
    }

    properties {
        // 동시 빌드 방지 설정        
        disableConcurrentBuilds()
        // Trigger
        if (config.trigger) {
            pipelineTriggers([cron(config.trigger)])
        }
    }

    // pipeline
    definition {
      cps {
        script(pipelineScript)
        sandbox(false)
      }
    }
  }

  if (config.view) {
    // 단일 view인 경우와 여러 view인 경우 모두 처리
    def views = config.view instanceof List ? config.view : [config.view]
    
    views.each { viewName ->
      // List View 생성 또는 업데이트
      dslFactory.listView(viewName) {
        description("Auto-generated view for ${viewName}")
        jobs {
          name(config.name)
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
    }
  }

  return job
}

static getFormatterDate () {
    def yesterday = new Date().minus(1)
    def timeZone = TimeZone.getTimeZone('Asia/Seoul')
    def dateFormat = new java.text.SimpleDateFormat('yyyy-MM-dd')
    dateFormat.setTimeZone(timeZone)
    def formattedDate = dateFormat.format(yesterday)
    return dateFormat.format(yesterday)
}

return this