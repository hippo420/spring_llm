exchange_rate	bas_dd	character varying	Y	기준일자 (PK, YYYYMMDD)
exchange_rate	cur_unit	character varying	Y	통화코드 (PK, 예: USD, JPY(100), EUR)
exchange_rate	bkpr	numeric		장부가격
exchange_rate	cur_nm	character varying		국가 및 통화명 (예: 미국 달러)
exchange_rate	kftc_bkpr	numeric		서울외국환중개 장부가격
exchange_rate	kftc_deal_bas_r	numeric		서울외국환중개 매매기준율
exchange_rate	deal_bas_r	numeric		매매기준율 (Deal Basis Rate)
exchange_rate	ten_dd_efee_r	numeric		10일환가료율
exchange_rate	ttb	numeric		전신환(송금) 받으실 때 환율 (Telegraphic Transfer Buying)
exchange_rate	tts	numeric		전신환(송금) 보내실 때 환율 (Telegraphic Transfer Selling)
exchange_rate	yy_efee_r	numeric		년환가료율

dart_account	id	bigint	Y	고유 식별자 (PK)
dart_account	account_nm	character varying		계정명 (예: 유동자산)
dart_account	bfefrmtrm_amount	numeric		전전기 금액
dart_account	bfefrmtrm_dt	character varying		전전기 일자
dart_account	bfefrmtrm_nm	character varying		전전기 명 (예: 제 19 기)
dart_account	bsns_year	character varying		사업연도
dart_account	corp_code	character varying		고유번호 (DART 발급)
dart_account	currency	character varying		통화 (예: KRW)
dart_account	frmtrm_add_amount	numeric		전기 누적 금액
dart_account	frmtrm_amount	numeric		전기 금액
dart_account	frmtrm_dt	character varying		전기 일자
dart_account	frmtrm_nm	character varying		전기 명 (예: 제 20 기)
dart_account	fs_div	character varying		개별/연결 구분 (CFS:연결재무제표, OFS:재무제표)
dart_account	fs_nm	character varying		개별/연결 명칭
dart_account	ord	integer		계정과목 정렬 순서
dart_account	rcept_no	character varying		접수번호
dart_account	reprt_code	character varying		보고서 코드 (예: 11011 사업보고서)
dart_account	sj_div	character varying		재무제표 구분 (BS:재무상태표, IS:손익계산서 등)
dart_account	sj_nm	character varying		재무제표 명칭
dart_account	stock_code	character varying		종목코드
dart_account	thstrm_add_amount	numeric		당기 누적 금액
dart_account	thstrm_amount	numeric		당기 금액
dart_account	thstrm_dt	character varying		당기 일자
dart_account	thstrm_nm	character varying		당기 명 (예: 제 21 기)

earnings_release	id	bigint	Y	고유 식별자 (PK)
earnings_release	isu_srd_cd	character varying		종목코드 또는 종목표준코드
earnings_release	net_income	numeric		당기순이익
earnings_release	operating_profit	numeric		영업이익
earnings_release	rawdisclosures	jsonb		원본 공시 데이터 (JSONB 형태)
earnings_release	report_quarter	character varying		보고 분기 (예: 1Q, 2Q, 3Q, 4Q)
earnings_release	report_year	integer		보고 연도 (예: 2026)
earnings_release	revenue	numeric		매출액
earnings_release	yoy_growth	double precision		전년 동기 대비 성장률 (YoY, %)
earnings_release	summary	character varying		실적 발표 요약
earnings_release	unit	numeric		금액 단위 (예: 1000000 = 백만원)
earnings_release	qoq_growth	double precision		전분기 대비 성장률 (QoQ, %)

economic_events	id	character varying	Y	고유 식별자 (PK)
economic_events	actual	character varying		실제 발표 수치
economic_events	category	character varying		지표 카테고리 (예: 고용, 물가, 중앙은행, GDP 등)
economic_events	consensus	character varying		시장 예상치 (컨센서스)
economic_events	country	character varying		발표 국가 (예: US, KR, CN, EU)
economic_events	event	character varying		경제 지표 및 이벤트 명칭 (예: 미국 소비자물가지수(CPI))
economic_events	eventdate	character varying		이벤트 발표 일자 (예: 2026-07-18)
economic_events	eventtime	character varying		이벤트 발표 시간 원본
economic_events	forecast	character varying		예측/전망치
economic_events	impact	character varying		시장 영향도 및 중요도 (예: High, Medium, Low 또는 1~3)
economic_events	previous	character varying		이전 발표 수치
economic_events	event_time	character varying		표준화된 이벤트 발생 시간 (timestamp 변환용)

info_stock	isu_cd	character varying	Y	종목코드
info_stock	reg_date	timestamp without time zone		등록일
info_stock	upd_date	timestamp without time zone		수정일
info_stock	isu_abbrv	character varying		종목단축명
info_stock	isu_eng_nm	character varying		종목영문명
info_stock	isu_nm	character varying		종목명
info_stock	isu_srt_cd	character varying		종목단축코드 (예: 005930)
info_stock	kind_stkcert_tp_nm	character varying		주권 구분 (보통주/우선주)
info_stock	list_dd	character varying		상장일자 (YYYYMMDD)
info_stock	list_shrs	bigint		상장 주식 수
info_stock	mkt_tp_nm	character varying		시장 구분 (코스피/코스닥/코넥스)
info_stock	parval	character varying		액면가
info_stock	sect_tp_nm	character varying		소속부 명칭 (중견기업부, 우량기업부 등)
info_stock	secugrp_nm	character varying		증권 그룹 명칭
info_stock	corp_code	character varying		고유번호 (DART 조회용)
info_stock	acc_mt	character varying		결산월 (예: 12)
info_stock	adres	character varying		본점 주소
info_stock	bizr_no	character varying		사업자등록번호
info_stock	ceo_nm	character varying		대표이사명
info_stock	est_dt	character varying		설립일자 (YYYYMMDD)
info_stock	fax_no	character varying		팩스번호
info_stock	hm_url	character varying		홈페이지 주소
info_stock	induty_code	character varying		업종코드
info_stock	ir_url	character varying		IR 홈페이지 주소
info_stock	phn_no	character varying		전화번호
info_stock	industry_code	character varying		산업/섹터 코드 (PK)
info_stock	themecode	character varying		테마 코드 (PK)
info_stock	delist_yn	character varying		상장폐지 여부 (Y/N)
info_stock	isu_abbrv2	character varying		종목단축명 보조

investor_trade_feature	bas_dd	character varying	Y	기준일자 (YYYYMMDD)
investor_trade_feature	isu_cd	character varying	Y	종목코드
investor_trade_feature	acc_trdval	bigint		누적 거래대금
investor_trade_feature	acc_trdvol	bigint		누적 거래량
investor_trade_feature	dominant_actor	character varying		주도 주체 (예: 외인, 기관, 개인)
investor_trade_feature	fluc_rt	numeric		등락률 (%)
investor_trade_feature	frgn_buy_ratio	numeric		외국인 매수 비중 (%)
investor_trade_feature	frgn_net_val	bigint		외국인 순매수 금액
investor_trade_feature	frgn_reg_ratio	numeric		외국인 보유 비중 (지분율, %)
investor_trade_feature	frgn_signal	integer		외국인 매매 시그널 (예: 1=매수, -1=매도, 0=중립)
investor_trade_feature	inst_buy_ratio	numeric		기관 매수 비중 (%)
investor_trade_feature	inst_signal	integer		기관 매매 시그널 (예: 1=매수, -1=매도, 0=중립)
investor_trade_feature	market_regime	character varying		시장 국면 (예: 상승장, 하락장, 횡보장)
investor_trade_feature	net_buy_frgn_qty	bigint		외국인 순매수 수량
investor_trade_feature	net_buy_inst_qty	bigint		기관 순매수 수량
investor_trade_feature	net_buy_prsn_qty	bigint		개인 순매수 수량
investor_trade_feature	net_buy_tot_qty	bigint		전체 순매수 수량
investor_trade_feature	tdd_clsprc	integer		당일 종가
investor_trade_feature	tdd_hgprc	integer		당일 고가
investor_trade_feature	tdd_lwprc	integer		당일 저가
investor_trade_feature	tdd_opnprc	integer		당일 시가
investor_trade_feature	tot_signal	integer		종합 매매 시그널 (예: 1=강세, -1=약세)

pile_trade	isu_srt_cd	character varying	Y	종목단축코드 (PK)
pile_trade	stck_bsop_date	character varying	Y	주식영업일자 (PK, YYYYMMDD)
pile_trade	acml_tr_pbmn	character varying		누적 거래 대금
pile_trade	acml_vol	character varying		누적 거래량
pile_trade	bank_ntby_qty	character varying		은행 순매수 수량
pile_trade	bank_ntby_tr_pbmn	character varying		은행 순매수 대금
pile_trade	bank_seln_tr_pbmn	character varying		은행 매도 대금
pile_trade	bank_seln_vol	character varying		은행 매도 수량
pile_trade	bank_shnu_tr_pbmn	character varying		은행 매수 대금
pile_trade	bank_shnu_vol	character varying		은행 매수 수량
pile_trade	bold_yn	character varying		굵은글씨 여부 (UI용)
pile_trade	etc_corp_ntby_tr_pbmn	character varying		기타법인 순매수 대금
pile_trade	etc_corp_ntby_vol	character varying		기타법인 순매수 수량
pile_trade	etc_corp_seln_tr_pbmn	character varying		기타법인 매도 대금
pile_trade	etc_corp_seln_vol	character varying		기타법인 매도 수량
pile_trade	etc_corp_shnu_tr_pbmn	character varying		기타법인 매수 대금
pile_trade	etc_corp_shnu_vol	character varying		기타법인 매수 수량
pile_trade	etc_ntby_qty	character varying		기타 순매수 수량
pile_trade	etc_ntby_tr_pbmn	character varying		기타 순매수 대금
pile_trade	etc_orgt_ntby_tr_pbmn	character varying		기타금융 순매수 대금
pile_trade	etc_orgt_ntby_vol	character varying		기타금융 순매수 수량
pile_trade	etc_orgt_seln_tr_pbmn	character varying		기타금융 매도 대금
pile_trade	etc_orgt_seln_vol	character varying		기타금융 매도 수량
pile_trade	etc_orgt_shnu_tr_pbmn	character varying		기타금융 매수 대금
pile_trade	etc_orgt_shnu_vol	character varying		기타금융 매수 수량
pile_trade	etc_seln_tr_pbmn	character varying		기타 매도 대금
pile_trade	etc_seln_vol	character varying		기타 매도 수량
pile_trade	etc_shnu_tr_pbmn	character varying		기타 매수 대금
pile_trade	etc_shnu_vol	character varying		기타 매수 수량
pile_trade	frgn_nreg_askp_pbmn	character varying		외국인비등록 매도호가 대금
pile_trade	frgn_nreg_askp_qty	character varying		외국인비등록 매도호가 수량
pile_trade	frgn_nreg_bidp_pbmn	character varying		외국인비등록 매수호가 대금
pile_trade	frgn_nreg_bidp_qty	character varying		외국인비등록 매수호가 수량
pile_trade	frgn_nreg_ntby_pbmn	character varying		외국인비등록 순매수 대금
pile_trade	frgn_nreg_ntby_qty	character varying		외국인비등록 순매수 수량
pile_trade	frgn_ntby_qty	character varying		외국인 순매수 수량
pile_trade	frgn_ntby_tr_pbmn	character varying		외국인 순매수 대금
pile_trade	frgn_reg_askp_pbmn	character varying		외국인등록 매도호가 대금
pile_trade	frgn_reg_askp_qty	character varying		외국인등록 매도호가 수량
pile_trade	frgn_reg_bidp_pbmn	character varying		외국인등록 매수호가 대금
pile_trade	frgn_reg_bidp_qty	character varying		외국인등록 매수호가 수량
pile_trade	frgn_reg_ntby_pbmn	character varying		외국인등록 순매수 대금
pile_trade	frgn_reg_ntby_qty	character varying		외국인등록 순매수 수량
pile_trade	frgn_seln_tr_pbmn	character varying		외국인 매도 대금
pile_trade	frgn_seln_vol	character varying		외국인 매도 수량
pile_trade	frgn_shnu_tr_pbmn	character varying		외국인 매수 대금
pile_trade	frgn_shnu_vol	character varying		외국인 매수 수량
pile_trade	fund_ntby_qty	character varying		투신 순매수 수량
pile_trade	fund_ntby_tr_pbmn	character varying		투신 순매수 대금
pile_trade	fund_seln_tr_pbmn	character varying		투신 매도 대금
pile_trade	fund_seln_vol	character varying		투신 매도 수량
pile_trade	fund_shnu_tr_pbmn	character varying		투신 매수 대금
pile_trade	fund_shnu_vol	character varying		투신 매수 수량
pile_trade	insu_ntby_qty	character varying		보험 순매수 수량
pile_trade	insu_ntby_tr_pbmn	character varying		보험 순매수 대금
pile_trade	insu_seln_tr_pbmn	character varying		보험 매도 대금
pile_trade	insu_seln_vol	character varying		보험 매도 수량
pile_trade	insu_shnu_tr_pbmn	character varying		보험 매수 대금
pile_trade	insu_shnu_vol	character varying		보험 매수 수량
pile_trade	ivtr_ntby_qty	character varying		투자자 순매수 수량
pile_trade	ivtr_ntby_tr_pbmn	character varying		투자자 순매수 대금
pile_trade	ivtr_seln_tr_pbmn	character varying		투자자 매도 대금
pile_trade	ivtr_seln_vol	character varying		투자자 매도 수량
pile_trade	ivtr_shnu_tr_pbmn	character varying		투자자 매수 대금
pile_trade	ivtr_shnu_vol	character varying		투자자 매수 수량
pile_trade	mrbn_ntby_qty	character varying		종금 순매수 수량
pile_trade	mrbn_ntby_tr_pbmn	character varying		종금 순매수 대금
pile_trade	mrbn_seln_tr_pbmn	character varying		종금 매도 대금
pile_trade	mrbn_seln_vol	character varying		종금 매도 수량
pile_trade	mrbn_shnu_tr_pbmn	character varying		종금 매수 대금
pile_trade	mrbn_shnu_vol	character varying		종금 매수 수량
pile_trade	orgn_ntby_qty	character varying		기관 순매수 수량
pile_trade	orgn_ntby_tr_pbmn	character varying		기관 순매수 대금
pile_trade	orgn_seln_tr_pbmn	character varying		기관 매도 대금
pile_trade	orgn_seln_vol	character varying		기관 매도 수량
pile_trade	orgn_shnu_tr_pbmn	character varying		기관 매수 대금
pile_trade	orgn_shnu_vol	character varying		기관 매수 수량
pile_trade	pe_fund_ntby_tr_pbmn	character varying		사모펀드 순매수 대금
pile_trade	pe_fund_ntby_vol	character varying		사모펀드 순매수 수량
pile_trade	pe_fund_seln_tr_pbmn	character varying		사모펀드 매도 대금
pile_trade	pe_fund_seln_vol	character varying		사모펀드 매도 수량
pile_trade	pe_fund_shnu_tr_pbmn	character varying		사모펀드 매수 대금
pile_trade	pe_fund_shnu_vol	character varying		사모펀드 매수 수량
pile_trade	prdy_ctrt	character varying		전일 대비율 (등락률)
pile_trade	prdy_vrss	character varying		전일 대비 (증감액)
pile_trade	prdy_vrss_sign	character varying		전일 대비 부호 (1:상한, 2:상승, 3:보합, 4:하한, 5:하락)
pile_trade	prsn_ntby_qty	character varying		개인 순매수 수량
pile_trade	prsn_ntby_tr_pbmn	character varying		개인 순매수 대금
pile_trade	prsn_seln_tr_pbmn	character varying		개인 매도 대금
pile_trade	prsn_seln_vol	character varying		개인 매도 수량
pile_trade	prsn_shnu_tr_pbmn	character varying		개인 매수 대금
pile_trade	prsn_shnu_vol	character varying		개인 매수 수량
pile_trade	scrt_ntby_qty	character varying		금융투자(증권) 순매수 수량
pile_trade	scrt_ntby_tr_pbmn	character varying		금융투자(증권) 순매수 대금
pile_trade	scrt_seln_tr_pbmn	character varying		금융투자(증권) 매도 대금
pile_trade	scrt_seln_vol	character varying		금융투자(증권) 매도 수량
pile_trade	scrt_shnu_tr_pbmn	character varying		금융투자(증권) 매수 대금
pile_trade	scrt_shnu_vol	character varying		금융투자(증권) 매수 수량
pile_trade	stck_clpr	character varying		주식 종가
pile_trade	stck_hgpr	character varying		주식 고가
pile_trade	stck_lwpr	character varying		주식 저가
pile_trade	stck_oprc	character varying		주식 시가

sector_trade_trend	industry_code	character varying	Y	산업/섹터 코드 (PK)
sector_trade_trend	stck_bsop_date	character varying	Y	주식 영업 일자 (PK, YYYYMMDD)
sector_trade_trend	frgn_ntby_qty	bigint		외국인 순매수 수량
sector_trade_trend	frgn_ntby_tr_pbmn	bigint		외국인 순매수 대금
sector_trade_trend	fund_ntby_tr_pbmn	bigint		투신 순매수 대금
sector_trade_trend	orgn_ntby_qty	bigint		기관 순매수 수량
sector_trade_trend	orgn_ntby_tr_pbmn	bigint		기관 순매수 대금
sector_trade_trend	pe_fund_ntby_tr_pbmn	bigint		사모펀드 순매수 대금
sector_trade_trend	prsn_ntby_qty	bigint		개인 순매수 수량
sector_trade_trend	prsn_ntby_tr_pbmn	bigint		개인 순매수 대금
sector_trade_trend	total_tr_pbmn	bigint		해당 섹터 전체 거래 대금
sector_trade_trend	total_vol	bigint		해당 섹터 전체 거래량

stock_trade	id	bigint	Y	일련번호 (PK)
stock_trade	acc_trdval	numeric		누적 거래대금
stock_trade	acc_trdvol	bigint		누적 거래량
stock_trade	bas_dd	character varying		기준 일자 (YYYYMMDD)
stock_trade	cmpprevdd_prc	bigint		전일 대비 등락폭 (전일비)
stock_trade	fluc_rt	numeric		등락률 (전일 대비 퍼센트)
stock_trade	isu_cd	character varying		종목 코드 (Standard Code)
stock_trade	isu_nm	character varying		종목명
stock_trade	list_shrs	bigint		상장 주식 수
stock_trade	mkt_nm	character varying		시장명 (KOSPI, KOSDAQ, KONEX)
stock_trade	mktcap	bigint		시가총액
stock_trade	sect_tp_nm	character varying		소속부명/섹터명
stock_trade	tdd_clsprc	bigint		당일 종가
stock_trade	tdd_hgprc	numeric		당일 고가
stock_trade	tdd_lwprc	numeric		당일 저가
stock_trade	tdd_opnprc	numeric		당일 시가
stock_trade	isu_srt_cd	character varying		종목단축코드