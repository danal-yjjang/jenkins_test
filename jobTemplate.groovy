job('(test)youngji_2') {
  description('seed job으로 생성한 자식 job')
  
  parameters {
    stringParam('date', descrption = 'ex) 2024-12-12')
    choiceParam('service code', ['LGU', 'SKT', 'SKTL'])
  }
  
  // SCM 설정 (None)
  scm {
    none {}
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
   
  // 빌드 후 조치
  publishers {
    // SSH
    publishOverSsh {
      server ('배치실행서버') {
        verbose(true)
        transferSet {
          sourceFiles('')
          removePrefix('')
          remoteDirectory('')
          execCommand('''
cd /home/service/smart-settlement-batch
max_dir=$(ls -d */ | grep -E '^[0-9]+/$' | tr -d '/' | sort -n | tail -n 1)
echo ">>>>>>>>>>>>>>>> 최대 수의 디렉터리 : $max_dir....."

echo "Build number: ${BUILD_NUMBER}"
echo "Jenkins job: ${JOB_NAME}"
echo "Workspace: ${WORKSPACE}"
echo "Custom parameter: ${CUSTOM_PARAM}"


java -Xms256m -Xmx1G -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/home/logs/ -XX:MaxMetaspaceSize=512m -jar /home/service/smart-settlement-batch/${max_dir}/*.jar --job.name=MOBILE_PHONE_RECEIVE_BEFORE_TARGET_LIST_CREATE_JOB chunkSize=$chunkSize month=$month workSize=$workSize
exitCodeJava=$?
echo ">>>>>>>>>>>>>>>>>>>>>>>> 프로세스를 종료합니다 - $exitCodeJava"
exit $exitCodeJava
sleep 60
                    ''')
              execTimeout(120000) 
        }
      }
    }
                
    // Groovy Postbuild 스크립트 추가
    groovyPostBuild {
      script('''
def alarmCurl(errorCode) {
    def env = manager.build.getEnvironment(manager.listener)
    def command = "curl --connect-timeout 5 -G -v \\"http://aramserver.com/~auth/AlarmGW\\" " +
            "-d \\"SYSTEM_ID=341\\" " +
            "-d \\"APPENDER=COROWN_SCH_DEV\\" " +
            "--data-urlencode \\"ERROR_MESSAGE= ${env['JOB_NAME']} 에러코드: ${errorCode} !! \\""

    manager.build.keepLog(true)

    def process = ['bash', '-c', command].execute()
    def output = new StringBuffer()
    def error = new StringBuffer()
    process.consumeProcessOutput(output, error)
    process.waitFor()

    println "Alarm Output: ${output.toString()}"
    if (error) {
        println "Curl Error : ${error.toString()}"
    }
}

// 로그에서 에러 패턴 검사
manager.listener.logger.println("실패 로그 패턴 찾는 중...")
def buildLog = manager.build.getLog()
def errorFound = false
def errorLines = []

// 에러 감지를 위한 패턴과 제외할 키워드 정의
def pattern = ~/.*(FATAL |Exec timed out or was interrupted after |OutOfMemoryErrorOutOfMemoryError|StackOverflowError|NullPointerException|APPLICATION FAILED TO START).*/
def excludeKeywords = ['elastic', '-XX:']

// 로그 라인별로 에러 패턴 검사
buildLog.eachLine { line ->
    def matcher = line =~ pattern
    if (matcher.matches()) {
        def shouldExclude = excludeKeywords.any { keyword ->
            line.toLowerCase().contains(keyword.toLowerCase())
        }

        if (!shouldExclude) {
            errorFound = true
            manager.listener.logger.println("감지된 에러 패턴:")
            manager.listener.logger.println(matcher.group(1))
            errorLines << matcher.group(1)
        }
    }
}

// 에러 발견 여부에 따른 배지 추가 및 알람 전송
if (errorFound) {
    manager.addErrorBadge("빌드 로그에서 에러가 발견되었습니다")
    def summary = manager.createSummary("error.gif")
    manager.listener.logger.println("에러발견")
    manager.listener.logger.println(errorLines)
    manager.buildFailure()
    alarmCurl("⚠" + errorLines.take(10) + "\\n로그에서 에러패턴 감지")
} else {
    manager.addBadge("success.gif", "에러가 발견되지 않았습니다")
    manager.listener.logger.println("에러가 발견되지 않았습니다")
}

// Spring Batch 종료 상태 코드 분석
def matcher = manager.getLogMatcher(".*Exec exit status not zero\\\\. Status \\\\[(\\\\d+)\\\\].*")
if (matcher?.matches()) {
    def exitStatus = Integer.parseInt(matcher.group(1))
    def batchStatusMap = [
            1: ["STARTING", "시작 전 배치가 멈췄습니다.", false],
            2: ["STARTED", "시작 단계에서 배치가 멈췄습니다.", false],
            3: ["STOPPING", "배치가 중단됐습니다.", false],
            4: ["STOPPED", "배치가 중단됐습니다.", false],
            5: ["FAILED", "❌❌배치가 실패했습니다.", true],
            6: ["ABANDONED", "❌❌배치가 강제중단됐습니다.", true],
            7: ["UNKNOWN", "❌❌배치 상태가 확인불가합니다.", true]
    ]

    def statusInfo = batchStatusMap.getOrDefault(exitStatus,
            [exitStatus.toString(), "❌❌배치 상태가 확인불가합니다.", true])

    def resultStatus = statusInfo[0]
    def msg = statusInfo[1]
    def needAlarm = statusInfo[2]

    Thread.currentThread().executable.setDescription("EXCD : " + resultStatus)
    manager.listener.logger.println("Found exit status: " + resultStatus)

    if (needAlarm || exitStatus > 7) {
        alarmCurl(exitStatus + " " + resultStatus + "\\n" + msg)
    }
} else {
    manager.listener.logger.println("Exit status not found in log")
    Thread.currentThread().executable.setDescription("")
}
		''')
      sandbox(false)  // 스크립트 실패 시 빌드 실패로 표시?
	}
  }
}
