class BatchJobTemplate {
  static job (dslFactory, Map config) {

    def paramScript = ""

    if (config.parameters) {
      config.parameters.each { param ->
        paramScript += " ${param.name}=\$${param.name}"
      }
    }

    def batchExecCommand = """
cd /home/service/smart-settlement-batch
max_dir=\$(ls -d */ | grep -E '^[0-9]+/\$' | tr -d '/' | sort -n | tail -n 1)
echo ">>>>>>>>>>>>>>>> 최대 수의 디렉터리 : \$max_dir....."    
    
java -Xms256m -Xmx1G -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/home/logs/ -XX:MaxMetaspaceSize=512m -jar /home/service/smart-settlement-batch/\${max_dir}/*.jar --job.name=${config.jobName}${paramScript}

exitCodeJava=\$?
echo ">>>>>>>>>>>>>>>> 프로세스를 종료합니다 - \$exitCodeJava"
exit \$exitCodeJava
sleep 60    
    """

    def batchAlarmSkript = getBatchAlarScript()

    def job = dslFactory.job(config.name) {
      description(config.description ?: config.name)
      
      // SCM 설정
      scm {
        none {}
      }

      parameters {
        if (config.parameters) {
          config.parameters.each { param ->
            switch(param.type) {
              case 'string':
              stringParam(param.name, param.defaultValue ?: '', param.description ?: '')
              break
              case 'boolean':
              booleanParam(param.name, param.defaultValue ?: false, param.description ?: '')
              break
              case 'choice':
              choiceParam(param.name, param.choices ?: [], param.description ?: '')
              break
            }
          }
        }
      }

      // 오래된 빌드 삭제 설정
      logRotator {
        daysToKeep(20)
        numToKeep(20)
      }

      // 빌드환경 설정
      wrappers {
        timestamps() // 콘솔 출력에 타임스탬프 추가
        colorizeOutput('xterm')  // ANSI 컬러 매핑
      }

      // 빌드 
      steps {
        shell('echo "$JOB_NAME 배치를 시작합니다."')
      }

      // 빌드후조치
      publishers {
        // SSH
        publishOverSsh {
          server(config.serverName ?: '배치실행서버') {
            verbose(true) // console log 출력력
            transferSet {
              sourceFiles('')
              removePrefix('')
              remoteDirectory('')
              execCommand(batchExecCommand)
            }
          }
        }
        
        // Groovy Postbuild 스크립트 추가
        groovyPostBuild {
          script(batchAlarmSkript)
        }
      }
    }

    // view 추가가
    if (config.view) {
      def viewName = config.view

      dslFactory.configure { project -> 
      def viewsNode = project / views

        // 기존 View 찾기
        def viewNode = null
        viewsNode.children().each { node ->
          if (node.name.text() == config.viewName) {
            viewNode = node
          }

          if (viewNode) {
            def jobNamesNode = viewNode / config.name

            boolean jobExists = false
            jobNamesNode.children().each { job ->
              if (job.text() == config.name) {
                jobExists = true
              }
            }
                  
            // 작업이 아직 없으면 추가
            if (!jobExists) {
              jobNamesNode << 'string'(config.name)
            }
          } else {
            println "경고: '${config.view}' View가 존재하지 않습니다. "
          }
        }
      }
    }

    return job
  }

  static getBatchAlarScript () {
    return """
def alarmCurl(errorCode) {
	def env = manager.build.getEnvironment(manager.listener)

    def alarmServer = env['ALARM_SERVER_URL'] ?: "http://172.16.233.33/~auth/AlarmGW"
    def systemId = env['ALARM_SYSTEM_ID'] ?: "341"
    def appender = env['ALARM_APPENDER'] ?: "COROWN_SCH_DEV"

	def command = "curl --connect-timeout 5 -G -v \\"" +alarmServer "\\" -d \\"SYSTEM_ID=" + systemId + "\\" -d \\"APPENDER=" + appender + "\\" --data-urlencode \\"ERROR_MESSAGE= \${env['JOB_NAME']} 에러코드: \${errorCode} !! \\""
    
    manager.build.keepLog(true)
	
	def process = ['bash', '-c', command].execute()
	def output = new StringBuffer()
	def error = new StringBuffer()
	process.consumeProcessOutput(output, error)
	process.waitFor()
	
	println "Alarm Output: \${output.toString()}"
	if (error) {
		println "Curl Error : \${error.toString()}"
	}
}

manager.listener.logger.println("실패 로그 패턴 찾는 중...")

def buildLog = manager.build.getLog()

def errorFound = false
def errorLines = []

def pattern = ~/.*(FATAL |Exec timed out or was interrupted after |OutOfMemoryErrorOutOfMemoryError|StackOverflowError|NullPointerException|APPLICATION FAILED TO START).*/
def excludeKeywords = [
  'elastic',
	'-XX:'
]

buildLog.eachLine { line ->
	def matcher = line =~ pattern
	if(matcher.matches()){
		def shouldExclude = excludeKeywords.any { keyword ->
			line.toLowerCase().contains(keyword.toLowerCase())
		}
		if (!shouldExclude) {
			errorFound = true
			manager.listener.logger.println("감지된 에러 패턴:")
			manager.listener.logger.println(matcher.group(1))
			errorLines << matcher.group(1);
		}
	}
}

if (errorFound) {
    manager.addErrorBadge("빌드 로그에서 에러가 발견되었습니다")
    def summary = manager.createSummary("error.gif")
	manager.listener.logger.println("에러발견")
    manager.listener.logger.println(errorLines)
	manager.buildFailure()
    alarmCurl("⚠"+errorLines.take(10)+"\n로그에서 에러패턴 감지")
} else {
    manager.addBadge("success.gif", "에러가 발견되지 않았습니다")
    manager.listener.logger.println("에러가 발견되지 않았습니다")
}


def matcher = manager.getLogMatcher(".*Exec exit status not zero\\. Status \\[(\\d+)\\].*")
if (matcher?.matches()) {
	def exitCode = Integer.parseInt(matcher.group(1))
    if (exitCode != 0) {
        def batchStatusMap = [
            1: ["STARTING", "시작 전 배치가 멈췄습니다.", false],
            2: ["STARTED", "시작 단계에서 배치가 멈췄습니다.", false],
            3: ["STOPPING", "배치가 중단됐습니다.", false],
            4: ["STOPPED", "배치가 중단됐습니다.", false],
            5: ["FAILED", "❌❌배치가 실패했습니다.", true],
            6: ["ABANDONED", "❌❌배치가 강제중단됐습니다.", false],
            7: ["UNKNOWN", "❌❌배치 상태가 확인불가합니다.", true]
        ]

        def statusInfo = batchStatusMap.getOrDefault(exitCode, 
                               [exitCode.toString(), "❌❌배치 상태가 확인불가합니다.", true])

        
        def resultStatus = statusInfo[0]
        def msg = statusInfo[1]
        def needAlarm = statusInfo[2]

        Thread.currentThread().executable.setDescription("EXCD : " + resultStatus)
        manager.listener.logger.println("Found exit status: " + resultStatus)

        if (needAlarm || exitCode > 7) {
            alarmCurl(exitCode + " " + resultStatus + "\\n" + msg)
        }

        Thread.currentThread().executable.setDescription("EXCD : " + resultStatus)
	    manager.listener.logger.println("Found exit status: "+resultStatus);
    }
} else {
	manager.listener.logger.println("Exit status not found in log");
	Thread.currentThread().executable.setDescription("")
}
    """
  }
}

BatchJobTemplate.job(this, [
    name : '2-원천사거래대사-20-상품권(RECONCILE_EXT_ORIGIN_MCBN_TX_JOB)', 
    jobName : 'RECONCILE_EXT_ORIGIN_MCBN_TX_JOB', 
    parameters : [
        [
            type : 'choice', 
            name : 'serviceCode',
            description : '서비스코드', 
            choices : ['BOOK_AND_LIFE', 'HAPPY_MONEY', 'CULTURE_LAND', 'CULTURE_GIFT']
        ], 
        [
            type : 'choice', 
            name : 'originalCompanyInstitutionCode',
            description : '원천사기관코드', 
            choices : ['BOOK_AND_LIFE', 'HAPPY_MONEY', 'CULTURE_LAND', 'CULTURE_GIFT']
        ], 
        [
            type : 'string', 
            name : 'date',
            description : '날짜'
        ]
    ], 
    view : '2-대사'
])

