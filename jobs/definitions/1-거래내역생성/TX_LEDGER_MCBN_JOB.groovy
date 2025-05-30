def template = evaluate(readFileFromWorkspace('jobs/templates/ApiJobTemplate.groovy'))

template.job(this, [
    name : '(test) 1-거래내역생성-01-상품권(TX_LEDGER_MCBN_JOB)-API', 
    jobName : 'TX_LEDGER_MCBN_JOB', 
    parameters : [
        [
            type : 'choice', 
            name : 'serviceCode',
            description : '서비스코드', 
            choices : ['BOOK_AND_LIFE', 'CULTURE_LAND', 'CULTURE_GIFT']
        ], 
        [
            type : 'string', 
            name : 'min'
        ],
        [
            type : 'string', 
            name : 'max'
        ]
    ], 
    view : '1-거래내역생성'
])

