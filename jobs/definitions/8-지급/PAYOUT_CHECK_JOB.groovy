def batchTemplate = evaluate(readFileFromWorkspace('jobs/templates/BatchJobTemplate.groovy'))

batchTemplate.job(this, [
    name : '(test) 8-가맹점지급-01-공통변수설정(PAYOUT_CHECK_JOB)', 
    jobName : 'PAYOUT_CHECK_JOB', 
    parameters : [
        [
            type : 'string', // 소문자로 작성
            name : 'poutScheDt',
            description : '2024-03-03'
        ], 
        [
            type : 'string', 
            name : 'poutExecDttm',
            description : '날짜', 
            defaultValue : '\$(date +"%Y-%m-%d %H:%M:%S")'
        ]
    ], 
    trigger : '30 7 * * *',
    afterJobs : [
        [
            jobName : '8-가맹점지급-02-지급임시테이블생성(PAYOUT_TEMPORARY_DATA_CREATE_JOB)', 
            currentParam : true
        ]
    ],
    view : 'test'
])