
def alarmCurl(errorCode) {
	def env = manager.build.getEnvironment(manager.listener)

    def alarmServer = env['ALARM_SERVER_URL'] ?: "http://172.16.233.33/~auth/AlarmGW"
    def systemId = env['ALARM_SYSTEM_ID'] ?: "341"
    def appender = env['ALARM_APPENDER'] ?: "COROWN_SCH_DEV"

	def command = "curl --connect-timeout 5 -G -v \"" +alarmServer "\" -d \"SYSTEM_ID=" + systemId + "\" -d \"APPENDER=" + appender + "\" --data-urlencode \"ERROR_MESSAGE= ${env['JOB_NAME']} 에러코드: ${errorCode} !! \""
    
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
            alarmCurl(exitCode + " " + resultStatus + "\n" + msg)
        }

        Thread.currentThread().executable.setDescription("EXCD : " + resultStatus)
	    manager.listener.logger.println("Found exit status: "+resultStatus);
    }
} else {
	manager.listener.logger.println("Exit status not found in log");
	Thread.currentThread().executable.setDescription("")
}
