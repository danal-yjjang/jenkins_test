### 개발


### 체크



### 특이한 케이스
- 1-거래내역생성-01-휴대폰(TB_SSS_MBPN_PMT_TX_L) : 빌드후조치 별개로 job exit 알람을 보낸다.
- 스케줄 trigger가 있는 batch job
- 8-가맹점지급-07-지급확정내역생성(PAYOUT_FIRM_LIST_CREATE_JOB) : 빌드 후 조치에서 다른 prooject 호출

추가할꺼
[x] 빌드를 원격으로 유발
[] java 옵션 변수화
[] 날짜 변수 추가가
[x] batch에 스케줄 추가
[] 빌드 후 조치 다른 프로젝트 호출


9-마감-03-01-수납기표내역-API-스케줄 -> 해당 케이스는 뭐지

-------
- 모든 param 값은 optional로 설정 : 빈값이면 제외된다. 
- param은 정의된 jobd의 param과 동일하게 설정하기 (chunksize, worksize는 생각가능)
  - 어떤 항목은 ui에 없고 script에만 있는 항목 들이 있음. 이거 헷갈리니까 ㄴㄴ
  - 대신 날짜 변수 제공 (TDATE, YDATE, DTTM -> default value 설정하기)