!macro customUnInstall
  ; Auto-updates uninstall the previous version silently before installing the new
  ; one. A MessageBox has no one to answer it then, so only ask (and only ever
  ; delete data) when this uninstaller is running interactively — i.e. the user
  ; actually launched "Uninstall 개인일정" themselves, not an in-place update.
  IfSilent cal_skipPrompt cal_askPrompt
  cal_askPrompt:
    MessageBox MB_YESNO|MB_ICONQUESTION "저장된 개인 일정 데이터(일정, 카테고리, 설정)도 함께 삭제하시겠습니까?$\n삭제하면 되돌릴 수 없습니다." IDYES cal_deleteData IDNO cal_keepData
    Goto cal_doneData
  cal_deleteData:
    RMDir /r "$APPDATA\personal-calendar"
    Goto cal_doneData
  cal_skipPrompt:
  cal_keepData:
  cal_doneData:
!macroend
