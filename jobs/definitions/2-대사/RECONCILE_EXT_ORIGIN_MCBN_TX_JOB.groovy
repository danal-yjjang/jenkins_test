def batchTemplate = evaluate(readFileFromWorkspace('jobs/templates/BatchJobTemplate.groovy'))
def scheduleTemplate = evaluate(readFileFromWorkspace('jobs/templates/ScheduleJobTemplate.groovy'))

batchTemplate.job(this, [
    name : '(test) 2-원천사거래대사-20-상품권(RECONCILE_EXT_ORIGIN_MCBN_TX_JOB)', 
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

scheduleTemplate.job(this, [
    name : '(test) 2-원천사거래대사-20-상품권(RECONCILE_EXT_ORIGIN_MCBN_TX_JOB)-스케줄',
    targetJobName : '(test) 2-원천사거래대사-20-상품권(RECONCILE_EXT_ORIGIN_MCBN_TX_JOB)',
    trigger : '* 4 * * *',
    parameters : [
        'serviceCode', 'originalCompanyInstitutionCode', 'date'
    ], 
    stages: [
        [name: '도서문화상품권', values: ['BOOK_AND_LIFE', 'BOOK_AND_LIFE', 'DATE']],
        [name: '컬쳐랜드상품권', values: ['CULTURE_LAND', 'CULTURE_LAND', 'DATE']],
        [name: '(주)문화상품권', values: ['CULTURE_GIFT', 'CULTURE_GIFT', 'DATE']]
    ],
    view : '2-대사'
])

