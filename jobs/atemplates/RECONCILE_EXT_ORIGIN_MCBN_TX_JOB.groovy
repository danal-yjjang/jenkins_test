def template = evaluate(readFileFromWorkspace('BatchJobTemplate.groovy'))

template.job(this, [
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

