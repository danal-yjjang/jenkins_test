def job (dslFactory, Map config) {

  def pipelineScript = """
pipeline {
    agent { node { label 'ScheduleNode' } }

    stages {
${config.stages ? config.stages.collect { stage ->
    return """        stage('${stage.name}') {
            steps {
                script {
                    listGenerate(${stage.values ? stage.values.collect { "'${it}'" }.join(', ') : ''})    
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
            pipelineTriggers {
                triggers {
                    cron {
                        spec(config.trigger)
                    }
                }   
            }
        }
    }

    // pipeline
    definition {
      cps {
        script(pipelineScript)
        sandbox(true)
      }
    }
  }

  // view 생성 또는 업데이트    
  if (config.view) {
    dslFactory.listView(config.view) {
      description("Auto-generated view for ${config.view}")
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

  return job
}

return this