def template = evaluate(readFileFromWorkspace('jobs/atemplates/BatchJobTemplate.groovy'))

template.job(this, [
    name : '(test) 11-휴대폰결제수납선행작업-01-다날수납대상내역생성(MOBILE_PHONE_RECEIVE_BEFORE_TARGET_LIST_CREATE_JOB)', 
    jobName : 'MOBILE_PHONE_RECEIVE_BEFORE_TARGET_LIST_CREATE_JOB', 
    parameters : [
        [
            type : 'string', 
            name : 'month',
            defaultValue : '\$(date -d "yesterday" +"%Y-%m")',
            description : '2024-06'
        ], 
        [
            type : 'string', 
            name : 'chunkSize',
            defaultValue : '10000'
        ], 
        [
            type : 'string', 
            name : 'workSize',
            defaultValue : '5',
            description : '병렬처리 수'
        ]
    ], 
    view : '11-휴대폰결제수납'
])

