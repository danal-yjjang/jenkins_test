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

def listGenerate(String serviceCode) {
    catchError(buildResult: 'FAILURE', stageResult: 'FAILURE'){
        def params = []
        ${config.parameters ? config.parameters.eachWithIndex { param, index -> 
            "        params.add(${param.type}(name:'${param.name}', value: values[${index}]))"
        }.join('\n') : ''}
        build job: '${config.targetJobName}', parameters: params
    }
}
  """

  def job = dslFactory.pipelineJob(config.name) {
    description(config.description ?: config.name)

    // 오래된 빌드 삭제 설정
    logRotator {
      daysToKeep(3)
    }

    // 동시 빌드 방지 설정
    disabled(false)

    // Trigger
    if (config.trigger) {
        triggers {
            cron(config.trigger)
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

return this