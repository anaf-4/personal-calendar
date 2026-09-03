!macro customUnInstall
  MessageBox MB_YESNO|MB_ICONQUESTION "저장된 개인 일정 데이터(일정, 카테고리, 설정)도 함께 삭제하시겠습니까?$\n삭제하면 되돌릴 수 없습니다." IDYES cal_deleteData IDNO cal_keepData
  cal_deleteData:
    RMDir /r "$APPDATA\personal-calendar"
    Goto cal_doneData
  cal_keepData:
  cal_doneData:
!macroend
