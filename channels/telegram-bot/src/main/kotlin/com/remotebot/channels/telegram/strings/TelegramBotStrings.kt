package com.remotebot.channels.telegram.strings

import com.remotebot.domain.model.BotLanguage

interface BotStrings {
    fun startMenuTitle(version: String, rootStatus: String): String
    fun startMenuSubtitle(): String
    fun btnBasic(): String
    fun btnMedia(): String
    fun btnComm(): String
    fun btnGeoSec(): String
    fun btnFiles(): String
    fun btnSystem(): String
    fun btnAutomation(): String
    fun btnAllCommands(): String
    fun btnStatus(): String
    fun btnBack(): String
    fun btnToggles(): String
    fun aboutTitle(version: String, rootStatus: String): String
    fun permissionsTitle(granted: Int, total: Int): String
    fun filterLabel(filter: String): String
    fun groupCriticalTitle(): String
    fun groupConfidentialTitle(): String
    fun groupStandardTitle(): String
    fun statusGranted(): String
    fun statusDenied(): String
    fun noPermissionsFound(): String
    fun permissionsUsageHint(): String
    fun statusTitle(): String
    fun statusServiceRunning(): String
    fun statusRoot(granted: Boolean): String
    fun statusConnectionMode(mode: String): String
    fun statusTelegramApiTitle(): String
    fun statusActiveNode(status: String): String
    fun statusStrategy(strategy: String): String
    fun statusEndpointsPool(): String
    fun incomingSmsTitle(from: String, sim: String): String
    fun incomingCallTitle(from: String, sim: String): String
    fun missedCallTitle(from: String, sim: String): String
    fun batteryChargingTitle(level: Int): String
    fun batteryLowTitle(level: Int): String
    fun batteryLevel(level: Int): String
    fun actionApproved(): String
    fun actionRejected(): String
    fun commandNotFound(cmd: String): String

    // Category Help Menus
    fun categoryBase(): String
    fun categoryMedia(): String
    fun categoryComm(): String
    fun categoryGeoSec(): String
    fun categoryFiles(): String
    fun categorySystem(): String
    fun categoryAuto(): String
    fun fullHelp(version: String, rootText: String): String
    fun radioUnknownCmd(cmd: String): String
    fun radioWifiNotFound(): String
    fun radioBtNotFound(): String
    fun radioCamServiceUnavailable(): String
    fun radioNoFlashCamera(): String
    fun radioTorchFail(e: String): String
    fun radioBtOnFail(): String
    fun radioBtOffFail(): String
    fun radioBtToggleFail(): String
    fun radioInvalidParam(u: String): String
    fun radioUsage(u: String): String
    fun radioAirplaneRootFail(): String
    fun radioHotspotStartFail(): String
    fun radioHotspotStopFail(): String
    fun radioHotspotToggleFail(): String
    fun radioDataOnFail(): String
    fun radioDataOffFail(): String
    fun radioDataToggleFail(): String
    fun radioGpsOnFail(): String
    fun radioGpsOffFail(): String
    fun radioGpsToggleFail(): String
    fun radioStateOnG(): String
    fun radioStateOffR(): String
    fun radioStateOffW(): String
    fun radioStateAirplaneOn(): String
    fun radioStateHotspotOn(): String
    fun radioStateHotspotOff(): String
    fun radioStateDataOn(): String
    fun radioStateDataOff(): String
    fun radioStateGpsOn(): String
    fun radioStateGpsOff(): String
    fun radioNotConnected(): String
    fun radioNoPerm(): String
    fun radioWordOn(): String
    fun radioWordOff(): String
    fun radioWordOnF(): String
    fun radioWordOffF(): String
    fun radioWifiStatus(state: String, ssid: String, rssi: Int, speed: String, ip: String): String
    fun radioBtStatus(state: String, name: String, bonded: Int): String
    fun radioWifiOnOk(): String
    fun radioWifiOnFailLong(): String
    fun radioWifiOffOk(): String
    fun radioWifiOffFailLong(): String
    fun radioWifiToggled(label: String): String
    fun radioWifiToggleFailLong(): String
    fun radioBtOnOk(): String
    fun radioBtOffOk(): String
    fun radioBtToggled(label: String): String
    fun radioTorchState(label: String): String
    fun radioTorchOnOk(): String
    fun radioTorchOffOk(): String
    fun radioTorchToggled(label: String): String
    fun radioRootActive(): String
    fun radioRootNote(): String
    fun radioAirplaneStatus(state: String, note: String): String
    fun radioNeedRootAirplane(): String
    fun radioAirplaneOnOk(): String
    fun radioAirplaneOffOk(): String
    fun radioAirplaneToggled(label: String): String
    fun radioHotspotStatus(state: String, note: String): String
    fun radioNeedRootHotspot(): String
    fun radioHotspotOnOk(): String
    fun radioHotspotOffOk(): String
    fun radioHotspotToggled(label: String): String
    fun radioDataStatus(state: String, note: String): String
    fun radioNeedRootData(): String
    fun radioDataOnOk(): String
    fun radioDataOffOk(): String
    fun radioDataToggled(label: String): String
    fun radioGpsStatus(state: String, note: String): String
    fun radioNeedRootGps(): String
    fun radioGpsOnOk(): String
    fun radioGpsOffOk(): String
    fun radioGpsToggled(label: String): String
    fun botLanguageCurrent(cur: String): String
    fun botLanguageSet(v: String): String
    fun botLanguageInvalid(v: String): String
    fun qsPanelBase(hasRoot: Boolean, wifiOn: Boolean, btOn: Boolean, flashOn: Boolean, autoBrightnessOn: Boolean, syncOn: Boolean): String
    fun qsPanelRoot(airplaneOn: Boolean, hotspotOn: Boolean, mobileDataOn: Boolean, gpsOn: Boolean, powerSaveOn: Boolean, adbOn: Boolean, adbWifiOn: Boolean, localIp: String, adbWifiPort: Int): String
    fun qsUnknownCmd(cmd: String): String
    fun qsOnWord(): String
    fun qsOffWord(): String
    fun ipNotFound(): String
    fun qsBtnTorch(on: Boolean): String
    fun qsBtnSync(on: Boolean): String
    fun qsBtnAirplane(on: Boolean): String
    fun qsBtnData(on: Boolean): String
    fun qsBtnSaver(on: Boolean): String
    fun qsBtnBright(on: Boolean): String
    fun qsBtnRefresh(): String
    fun qsRootTip(): String
    fun psStateOn(): String
    fun psStatus(state: String, note: String): String
    fun psNeedRoot(): String
    fun psOnOk(): String
    fun psOnFail(): String
    fun psOffOk(): String
    fun psOffFail(): String
    fun psToggled(label: String): String
    fun psToggleFail(): String
    fun abStateOn(): String
    fun abStateOff(): String
    fun abStatus(state: String): String
    fun abOnOk(): String
    fun abOnFail(): String
    fun abOffOk(): String
    fun abOffFail(): String
    fun abToggled(label: String): String
    fun abToggleFail(): String
    fun syncStatus(state: String): String
    fun syncOnOk(): String
    fun syncOffOk(): String
    fun syncToggled(label: String): String
    fun cbUnavailable(): String
    fun cbEmpty(): String
    fun cbClearBtn(): String
    fun cbContent(t: String): String
    fun cbSet(t: String): String
    fun adbNeedRoot(): String
    fun adbStatus(state: String, flag: Int): String
    fun adbOnOk(): String
    fun adbOffOk(): String
    fun adbUsage(): String
    fun awifiNeedRoot(): String
    fun awifiStateOn(port: Int): String
    fun awifiStatusOn(state: String, ip: String, port: Int): String
    fun awifiStatusOff(state: String, ip: String): String
    fun awifiOnOkA(port: Int, ip: String): String
    fun awifiOnOkB(port: Int, ip: String): String
    fun awifiUsage(): String
    fun auUnknownCmd(cmd: String): String
    fun auRecStopped(): String
    fun auNoActiveRec(): String
    fun auRecGoing(name: String): String
    fun auRecIdle(): String
    fun auRecPanel(status: String): String
    fun auRecOpName(dur: String): String
    fun auRecDescFile(dur: String): String
    fun auRecDescVoice(dur: String): String
    fun auRecCaptionFile(sec: Int): String
    fun auRecCaptionVoice(sec: Int): String
    fun auRecError(e: String): String
    fun auDurSec(s: Int): String
    fun auDurMin(m: Int): String
    fun auDurMinSec(m: Int, s: Int): String
    fun auBtnSec(n: String): String
    fun auBtnSecMin(n: String, m: String): String
    fun auBtnRecUntilStop(): String
    fun auBtnStopRec(): String
    fun auBtnRefreshPanel(): String
    fun auBtnToVoice(): String
    fun auBtnToFile(): String
    fun auTtsUsage(): String
    fun auTtsPlayed(t: String): String
    fun auTtsError(e: String): String
    fun auBtnSirenOn(): String
    fun auBtnSirenOff(): String
    fun auBtnRingNormal(): String
    fun auBtnSilent(): String
    fun auBtnVibrate(): String
    fun auBtnVolumeLevels(): String
    fun auBtnRefresh(): String
    fun auPlayerHead(mode: String): String
    fun auVolHead(): String
    fun auStreamRing(): String
    fun auStreamNotif(): String
    fun auStreamAlarm(): String
    fun auStreamMedia(): String
    fun auStreamCall(): String
    fun auStreamSystem(): String
    fun auVolHint(): String
    fun auBtnMedia50(): String
    fun auBtnMedia100(): String
    fun auBtnRing100(): String
    fun auBtnAlarm100(): String
    fun auVolSet(name: String, lvl: String, max: String): String
    fun auVolError(e: String): String
    fun auRingHead(mode: String): String
    fun auBtnNormal(): String
    fun auRingToggled(icon: String, mode: String): String
    fun auRingError(e: String): String
    fun auSirenOff(): String
    fun auSirenOp(): String
    fun auSirenOpDesc(): String
    fun auSirenOn(): String
    fun auSirenError(e: String): String
    fun auSdUsage(): String
    fun auSdOpName(lvl: String): String
    fun auSdOpDesc(db: String): String
    fun auSdActive(db: String): String
    fun auSdOff(): String
    fun auBtnSdOff(): String
    fun auBtnSdOn(lvl: String): String
    fun auSdPanel(status: String, lvl: String, db: String): String
    fun auB60(): String
    fun auB300(): String
    fun auB600(): String
    fun auB1800(): String
    fun auB3600(): String
    fun secUnknownCmd(cmd: String): String
    fun secUserListEmpty(): String
    fun secUserListBody(listStr: String): String
    fun secUserListCleared(): String
    fun secAddUserIdHint(): String
    fun secUserExists(id: String): String
    fun secUserAdded(id: String): String
    fun secDelUserIdHint(): String
    fun secUserNotFound(id: String): String
    fun secUserDeleted(id: String): String
    fun secAddDelUserFmt(): String
    fun secUserIdTgHint(): String
    fun secUserExistsTg(id: String): String
    fun secUserNotFoundTg(id: String): String
    fun secUserDeletedTg(id: String): String
    fun secPhoneListEmpty(): String
    fun secPhoneListBody(listStr: String): String
    fun secPhoneListCleared(): String
    fun secAddPhoneHint(): String
    fun secPhoneExists(phone: String): String
    fun secPhoneAdded(phone: String): String
    fun secDelPhoneHint(): String
    fun secPhoneNotFound(phone: String): String
    fun secPhoneDeleted(phone: String): String
    fun secAddPhonePhoneHint(): String
    fun secPhoneNoPinAdded(phone: String): String
    fun secDelPhonePhoneHint(): String
    fun secFwdListEmpty(): String
    fun secOnOff(on: Boolean): String
    fun secSmsFwdOn(): String
    fun secSmsFwdOff(): String
    fun secMissedFwd(enable: Boolean): String
    fun secPushFwd(enable: Boolean): String
    fun secChargeFwd(enable: Boolean): String
    fun secLowBattFwd(enable: Boolean): String
    fun secAllBattFwd(enable: Boolean): String
    fun secTranslitFwd(enable: Boolean): String
    fun secAddFwdHint(): String
    fun secFwdExists(phone: String): String
    fun secFwdAdded(phone: String): String
    fun secDelFwdHint(): String
    fun secFwdNotFound(phone: String): String
    fun secFwdDeleted(phone: String): String
    fun secFwdCleared(): String
    fun secFwdAddedShort(phone: String): String
    fun secAddFwdPhoneHint(): String
    fun secFwdAddedCalls(phone: String): String
    fun secDelFwdPhoneHint(): String
    fun secEventsEmpty(text: String): String
    fun secSubsHead(): String
    fun secSelfTag(): String
    fun secRoleCmds(): String
    fun secRoleRecv(): String
    fun secEventsHint(): String
    fun secUnknownCategory(c: String): String
    fun secStateLabel(on: Boolean): String
    fun secEventsOffSelf(id: String): String
    fun secEventsOnSelf(id: String): String
    fun secEventsOffUser(id: String): String
    fun secEventsOnUser(id: String): String
    fun secCategoryForUser(c: String, id: String, st: String): String
    fun secCategorySelf(c: String, st: String): String
    fun secEventsUsage(): String
    fun secFwdListBody(listStr: String, s1: String, s2: String, s3: String, s4: String, s5: String, s6: String): String
    fun secDelUserIdTgHint(): String
    fun secPhoneNotFoundTg(phone: String): String
    fun locUnknownCmd(cmd: String): String
    fun locAcquiring(t: String): String
    fun locOpName(t: String): String
    fun locOpDesc(t: String): String
    fun locAcquireError(e: String): String
    fun locTrackInactive(): String
    fun locTrackOpName(r: String): String
    fun locTrackOpDesc(r: String): String
    fun locTrackPanelState(act: Boolean): String
    fun locTrackPanelStateInt(act: Boolean, i: String): String
    fun locDurSec(s: Long): String
    fun locDurHour(h: Long): String
    fun locDurMin(m: Long): String
    fun locDurMinSec(m: Long, s: Long): String
    fun locStateGps(): String
    fun locStateLbs(): String
    fun locSignalDefault(): String
    fun locSignalGps(lvl: String): String
    fun locSignalGpsSats(used: String, total: String): String
    fun locSignalGpsLabel(): String
    fun locSignalNoGps(): String
    fun locSeqStr(n: String): String
    fun locTitleTracked(seq: String): String
    fun locTitleDevice(): String
    fun locAltitudeSpeed(alt: String, spd: String): String
    fun locCoord(lat: String, lon: String): String
    fun locAccuracy(a: String): String
    fun locMethod(m: String): String
    fun locTimeAddr(t: String, a: String): String
    fun locLinks(g: String, o: String): String
    fun locAddrBlock(a: String): String
    fun locBtnAgain(): String
    fun locBtnInstant(): String
    fun locBtnStartTrack(): String
    fun locBtnStopTrack(): String
    fun locBtnStatus(): String
    fun locBtnSingle(): String
    fun locBtn30s(): String
    fun locBtn1m(): String
    fun locBtn5m(): String
    fun locBtn15m(): String
    fun locBtn30m(): String
    fun locBtn60m(): String
    fun locGpsOn(): String
    fun locGpsOff(): String
    fun locGpsStatus(st: String): String
    fun locGpsOnOff(on: Boolean): String
    fun locGpsToggled(st: String): String
    fun locGpsToggleError(e: String): String
    fun locGpsUsage(): String
    fun locShakeOpName(lvl: String, cd: String): String
    fun locShakeOpDesc(th: String, cd: String): String
    fun locShakeCdLabel(s: String, act: Boolean): String
    fun locShakeBtnOff(): String
    fun locShakeBtnOn(lvl: String, cd: String): String
    fun locShakeBtnRefresh(): String
    fun locShakeActive(th: String, cd: String): String
    fun locShakeOff(): String
    fun locTrackStopped(points: String, m: String, s: String): String
    fun locTrackStatus(st: String, iv: String, points: String, dur: String): String
    fun locTrackStarted(r: String): String
    fun locTrackPanel(st: String): String
    fun locShakePanel(st: String, lvl: String, th: String, cd: String): String
    fun locResultBody(coords: String, acc: String, method: String, time: String, addr: String, gmaps: String, osm: String): String
    fun locSatsFragment(used: String, total: String): String

    fun bkpUnknownCmd(cmd: String): String
    fun bkpFileCaption(): String
    fun bkpJsonBlock(json: String): String
    fun bkpRestoreHelp(): String
    fun bkpRestored(version: String): String
    fun bkpUnknownError(): String
    fun bkpDeserializeError(err: String): String
    fun pxStatusOff(): String
    fun pxAuthWith(user: String): String
    fun pxAuthNone(): String
    fun pxWsPath(path: String): String
    fun pxStatusBlock(type: String, addr: String, auth: String, extra: String): String
    fun pxHelp(status: String): String
    fun pxDisabled(): String
    fun pxFormat(sub: String, tail: String): String
    fun pxBadPort(port: String): String
    fun pxSet(type: String, addr: String): String
    fun pxWssSet(url: String): String
    fun pxUnknownParam(sub: String, available: String): String
    fun shUnknownCmd(cmd: String): String
    fun shHelp(): String
    fun shNoOutput(code: Int): String
    fun shTruncated(limit: Int): String
    fun shResultBlock(emoji: String, mode: String, cmd: String, code: Int, out: String): String
    fun shTimeout(): String
    fun shExecError(msg: String): String
    fun shDelayedFooter(status: String): String
    fun shRootRestart(): String
    fun shRootShutdown(): String
    fun shRootRecovery(): String
    fun shRootBootloader(): String
    fun shStatusReboot(): String
    fun shStatusShutdown(): String
    fun shStatusRecovery(): String
    fun shStatusFastboot(): String
    fun faUnknownCmd(cmd: String): String
    fun faGetFileUsage(): String
    fun faArchiveCaption(name: String, size: String): String
    fun faZipError(msg: String): String
    fun faFileCaption(name: String, size: String): String
    fun faGetFileError(msg: String): String
    fun faDownloadUsage(): String
    fun faDownloaded(path: String, size: String): String
    fun faDownloadError(msg: String): String
    fun faDirEmpty(path: String): String
    fun faDirContentHeader(path: String): String
    fun faMoreItems(count: Int): String
    fun faDirReadError(msg: String): String
    fun faBtnUp(): String
    fun faBtnRefresh(): String
    fun faBtnZip(): String
    fun faExplorerHeader(path: String, dirs: Int, files: Int, total: Int): String
    fun faNoApps(): String
    fun faAppsHeader(count: Int): String
    fun faMoreApps(count: Int): String
    fun faLaunchHint(): String
    fun faLaunchUsage(): String
    fun faAppLaunched(pkg: String): String
    fun faLaunchError(msg: String): String
    fun faInstallHelp(): String
    fun faApkInstalled(path: String): String
    fun faInstallError(msg: String): String
    fun faNoProcesses(): String
    fun faTaskManagerHeader(): String
    fun faKillHint(): String
    fun faProcessListError(msg: String): String
    fun faKillUsage(): String
    fun faProcessKilled(target: String): String
    fun faKillError(msg: String): String
    fun faUnsentEmpty(): String
    fun faUnsentHeader(count: Int): String
    fun faUnsentLine(path: String, type: String, attempts: Int): String
    fun tnUnknownCmd(cmd: String): String
    fun tnSmsUsage(): String
    fun tnSmsNeedArgs(): String
    fun tnSmsSent(phone: String, sim: String, text: String): String
    fun tnSmsError(msg: String): String
    fun tnSmsInboxEmpty(): String
    fun tnSmsInboxHeader(limit: Int): String
    fun tnSmsReadError(msg: String): String
    fun tnCallUsage(): String
    fun tnCallNeedNumber(): String
    fun tnCallInitiated(phone: String, sim: String): String
    fun tnCallError(msg: String): String
    fun tnCallsEmpty(): String
    fun tnCallsHeader(limit: Int): String
    fun tnCallDuration(min: Long, sec: Long): String
    fun tnCallMeta(time: String, type: String): String
    fun tnCallsReadError(msg: String): String
    fun tnUssdUsage(): String
    fun tnUssdNeedCode(): String
    fun tnUssdResponse(sim: String, code: String, response: String): String
    fun tnUssdError(msg: String): String
    fun tnNotifEmpty(): String
    fun tnNotifHeader(limit: Int): String
    fun tnNotifReplyBadge(id: String): String
    fun tnNotifyUsage(): String
    fun tnNotifyPosted(title: String, text: String): String
    fun tnNotifyError(msg: String): String
    fun tnFakeNotifyUsage(): String
    fun tnAppSber(): String
    fun tnAppTbank(): String
    fun tnFakeNotifySimulated(app: String, title: String, text: String): String
    fun tnFakeNotifyError(msg: String): String
    fun tnAnswerUsage(): String
    fun tnAnswerSent(id: String, text: String): String
    fun tnAnswerError(msg: String): String
    fun tnActUsage(): String
    fun tnActDone(index: Int, id: String): String
    fun tnActError(msg: String): String
    fun tnClearUsage(): String
    fun tnCleared(id: String): String
    fun tnClearError(msg: String): String
    fun tnRulesEmpty(): String
    fun tnRulesHeader(): String
    fun tnRuleAllow(): String
    fun tnRuleExclude(): String
    fun tnRuleAdded(id: String): String
    fun tnRuleDeleted(id: String): String
    fun tnRulesUsage(): String
    fun mdUnknownCmd(cmd: String): String
    fun mdLensFront(): String
    fun mdLensBack(): String
    fun mdLensFrontSelfie(): String
    fun mdLensBackRear(): String
    fun mdLensFrontLower(): String
    fun mdLensBackLower(): String
    fun mdFlashOn(): String
    fun mdFlashOff(): String
    fun mdPhotoCaption(lens: String, flash: String, rotation: String): String
    fun mdPhotoError(msg: String): String
    fun mdRecStopped(): String
    fun mdNoActiveRec(): String
    fun mdRecActive(name: String): String
    fun mdRecIdle(): String
    fun mdRecPanel(status: String, lens: String): String
    fun mdRecOpName(lens: String, dur: String): String
    fun mdRecOpDesc(lens: String, dur: String): String
    fun mdRecCaption(lens: String, sec: Int): String
    fun mdRecError(msg: String): String
    fun mdDurSec(sec: Int): String
    fun mdDurMin(min: Int): String
    fun mdDurMinSec(min: Int, sec: Int): String
    fun mdBtnToggleToBack(): String
    fun mdBtnToggleToFront(): String
    fun mdBtnDur(sec: Int): String
    fun mdBtnDurMin(sec: Int, min: Int): String
    fun mdBtnDurHour(sec: Int): String
    fun mdBtnRecUntilStop(): String
    fun mdBtnStopRec(): String
    fun mdBtnRefreshPanel(): String
    fun mdBtnStop(): String
    fun mdBtnDisable(): String
    fun mdBtnEnable(level: Int, cooldown: Int): String
    fun mdBtnRefresh(): String
    fun mdSec(sec: Int): String
    fun mdSecActive(sec: Int): String
    fun mdMotionActive(percent: Int, cooldown: Int): String
    fun mdMotionInactive(): String
    fun mdMotionPanel(status: String, level: Int, percent: Int, cooldown: Int): String
    fun mdMotionOpName(level: Int, cooldown: Int): String
    fun mdMotionOpDesc(percent: Int, cooldown: Int): String
    fun mdGalleryEmpty(): String
    fun mdGalleryCaption(count: Int): String
    fun siUnknownCmd(cmd: String): String
    fun siUptimeDays(d: Long, h: Long, m: Long): String
    fun siUptimeHours(h: Long, m: Long): String
    fun siNetCellular(): String
    fun siNetOff(): String
    fun siRootGranted(): String
    fun siRootDenied(): String
    fun siSummary(model: String, os: String, build: String, patch: String, root: String, uptime: String, cores: Int, abis: String, ramUsed: Long, ramTotal: Long, ramPct: Int, romAvail: String, romTotal: String, screen: String, sensors: Int, netType: String, ip: String): String
    fun siBtnTogglesPanel(): String
    fun siBtnBattery(): String
    fun siBtnNetworkInterfaces(): String
    fun siSysInfoError(msg: String): String
    fun siBatteryReadError(): String
    fun siBatCharging(): String
    fun siBatDischarging(): String
    fun siBatFull(): String
    fun siBatNotCharging(): String
    fun siBatUnknown(): String
    fun siPlugAc(): String
    fun siPlugUsb(): String
    fun siPlugWireless(): String
    fun siPlugBattery(): String
    fun siHealthGood(): String
    fun siHealthOverheat(): String
    fun siHealthDead(): String
    fun siHealthOverVoltage(): String
    fun siHealthCold(): String
    fun siHealthNormal(): String
    fun siVoltage(volts: String, mv: Int): String
    fun siBatteryBlock(icon: String, pct: Int, status: String, plug: String, temp: String, voltage: String, health: String, tech: String): String
    fun siPowerSaveOn(): String
    fun siPowerSaveOff(): String
    fun siBtnRefresh(): String
    fun siBatParamsError(msg: String): String
    fun siNoInterfaces(): String
    fun siIfaceCellular(): String
    fun siIfaceAbsent(): String
    fun siNetTitle(): String
    fun siNetPrimary(transport: String): String
    fun siNoAddresses(): String
    fun siBtnMobileData(): String
    fun siNetError(msg: String): String
    fun siUptimeText(app: String, os: String): String
    fun siNA(): String
    fun siBtnStatus(): String
    fun siBtnToggles(): String
    fun siUnitD(): String
    fun siUnitH(): String
    fun siUnitM(): String
    fun siUnitS(): String
    fun scuBtnDisableRoot(): String
    fun scuBtnEnableRoot(): String
    fun scuBtnDisableBolt(): String
    fun scuBtnEnableBolt(): String
    fun scuBtnDisable(): String
    fun scuBtnEnable(): String
    fun scuBtnAllToggles(): String
    fun scUnknownCmd(cmd: String): String
    fun srUnknownCmd(cmd: String): String
    fun srScreenshotCaption(method: String): String
    fun srScreenshotError(msg: String): String
    fun srRecOpName(sec: Int): String
    fun srRecOpDesc(): String
    fun srRecCaption(sec: Int): String
    fun srRecError(msg: String): String
    fun autUnknownCmd(cmd: String): String
    fun autTaskerInstalled(): String
    fun autTaskerMissing(): String
    fun autTaskerHelp(state: String): String
    fun autParams(params: String): String
    fun autTaskerSent(task: String, extra: String): String
    fun autTaskerError(msg: String): String
    fun autScriptUsage(): String
    fun autScriptOk(ms: Long, out: String): String
    fun autScriptErr(ms: Long, err: String, out: String): String
    fun autNoTimers(): String
    fun autTimersHeader(count: Int): String
    fun autIntervalRepeat(sec: Long): String
    fun autIntervalOnce(): String
    fun autTimerLine(id: String, cmd: String, time: String, interval: String): String
    fun autTimerDelHint(): String
    fun autBadDelay(delay: String): String
    fun autTimerCreated(id: String, cmd: String, delay: String): String
    fun autTimerDeleted(id: String): String
    fun autTimersUsage(): String
    fun evStatusOn(): String
    fun evStatusOff(): String
    fun evRoleCommandsOnly(): String
    fun evRoleRecipient(): String
    fun evUserSelf(id: Long): String
    fun evUserOther(id: Long): String
    fun evText(user: String, role: String, notif: String, sms: String, calls: String, system: String): String
    fun evBtnNotifOn(): String
    fun evBtnNotifOff(): String
    fun evBtnSmsOn(): String
    fun evBtnSmsOff(): String
    fun evBtnCallsOn(): String
    fun evBtnCallsOff(): String
    fun evBtnSystemOn(): String
    fun evBtnSystemOff(): String
    fun evBtnAllOff(): String
    fun evBtnAllOn(): String
    fun evBtnRefresh(): String
    fun bsRootAvail(): String
    fun bsRootMissing(): String
    fun bsRootActive0(): String
    fun bsRootRestricted(): String
    fun bsApiOk(ms: Long): String
    fun bsApiErr(err: String): String
    fun bsTimeout(): String
    fun bsTimeoutWord(): String
    fun bsLatency(ms: Long): String
    fun bsUnreachable(): String
    fun bsEndpointItem(root: String): String
    fun bsIndented(value: String): String
    fun bsPushTitle(): String
    fun bsGwLine(status: String): String
    fun bsFcmLine(status: String): String
    fun bsHmsLine(status: String): String
    fun bsPing(ms: Long): String
    fun bsEchoEmpty(): String
    fun bsEchoPrefix(): String
    fun bsUnknownCmd(cmd: String): String
    fun bsPermProviderMissing(): String
    fun bsPermLine(icon: String, name: String, state: String): String
    fun bsRefreshStatus(): String
    fun bmUnknownCmd(cmd: String): String
    fun bmNoOps(): String
    fun bmOpsHeader(count: Int): String
    fun bmOpDuration(sec: Long): String
    fun bmOpInfo(desc: String): String
    fun bmStopHint(): String
    fun bmStopped(count: Int): String
    fun bmLogEmpty(note: String): String
    fun bmLogFilterNote(level: String): String
    fun bmLogLevelHeader(level: String): String
    fun bmLogHeader(count: Int, header: String): String
    fun bmLogUsage(): String
    fun bmUnsentEmpty(): String
    fun bmActive(): String
    fun bmDisabled(): String
    fun bmInlineOn(): String
    fun bmInlineOff(): String
    fun bmStatusPanel(tg: String, mode: String, sms: String, tgSms: String, tgCalls: String, tgPush: String, tgCharge: String, tgLow: String, smsSms: String, smsCalls: String, smsPush: String, smsCharge: String, smsLow: String, smsTranslit: String, targets: Int): String
    fun bmTgForwardPanel(sms: String, calls: String, push: String, charge: String, low: String): String
    fun bmFwdTgSms(state: String): String
    fun bmFwdTgCalls(state: String): String
    fun bmFwdTgPush(state: String): String
    fun bmFwdTgCharge(state: String): String
    fun bmFwdTgLow(state: String): String
    fun bmFwdTgBattery(state: String): String
    fun bmTgForwardUnknownType(): String
    fun bmTgBotState(state: String): String
    fun bmTgBotUsage(): String
    fun bmTgBotResult(state: String): String
    fun bmSmsBotState(state: String): String
    fun bmSmsBotUsage(): String
    fun bmSmsBotResult(state: String): String
    fun bmBotToggleUsage(): String
    fun bmUnknownChannel(channel: String): String
    fun bmStateOn(): String
    fun bmStateOff(): String
    fun bmEnabledFem(): String
    fun bmDisabledFem(): String
    fun bmEnabledPlur(): String
    fun bmDisabledPlur(): String
    fun bmTgOnRes(): String
    fun bmTgOffRes(): String
    fun bmSmsOnRes(): String
    fun bmSmsOffRes(): String
    fun rsFileNotFound(path: String): String
    fun rsPhotoNotFound(path: String): String
    fun rsVideoNotFound(path: String): String
    fun rsVoiceNotFound(path: String): String
    fun rsAudioNotFound(path: String): String
    fun rsMediaGroupNotFound(): String
    fun rsSendError(msg: String): String
    fun engCmdError(msg: String): String
    fun engCbError(msg: String): String
    fun engApkTooBigCloud(sizeMb: Long, apk: String): String
    fun engApkDownloading(apk: String): String
    fun engApkTooBigSimple(apk: String): String
    fun engApkError(msg: String): String
    fun engBackupError(msg: String): String
    fun tfUnauthorizedPin(): String
    fun tfUnauthorizedNumber(): String
    fun tfErrorReply(msg: String): String
    fun tfIncomingSmsHeader(from: String, sim: String, time: String): String
    fun tfReplyHint(): String
    fun tfBtnReplySms(): String
    fun tfSmsForwardText(sender: String, body: String): String
    fun tfUnknownNumber(): String
    fun tfCallIncoming(): String
    fun tfCallMissed(): String
    fun tfFrom(): String
    fun tfMissedCallText(caller: String): String
    fun irEnterSmsText(): String
    fun irBtnCancel(): String
    fun irSmsReplyPrompt(phone: String, sim: String): String
    fun irCancelled(): String
    fun irSmsCancelled(): String
    fun irEnterReplyText(): String
    fun irNotifReplyPrompt(id: String): String
    fun ir2faDisabled(): String
    fun irActionDone(): String
    fun irActionError(msg: String): String
    fun irActionFailed(): String
    fun irActionStatusOk(time: String): String
    fun irActionStatusErr(msg: String): String
    fun irTimeout(): String
    fun irSettingsUpdated(): String
    fun irSmsSent(phone: String, sim: String, text: String): String
    fun irSmsSendFailed(): String
    fun irSmsSendError(phone: String, err: String): String
    fun irNotifReplyCancelled(): String
    fun irReplyToAppSent(text: String): String
    fun irReplySendFailed(): String
    fun irNotifReplyError(id: String, err: String): String
    fun irQuickReplySent(text: String): String
    fun irQuickReplyFailed(): String
    fun irQuickReplyError(err: String): String
    fun efBtnReply(): String
    fun efShakeAlert(): String
    fun efSoundAlert(db: Int, threshold: Int, level: Int): String
    fun efMotionAlert(): String
    fun efChargingOn(): String
    fun efChargingOff(): String
    fun efLevel(level: Int): String
    fun efSource(plug: String): String
    fun efVoltage(mv: Int): String
    fun efOnBattery(): String
    fun efTemperature(temp: String): String
    fun efLowAlert(level: Int): String
    fun permName(id: String): String
    fun permDesc(id: String): String
    fun efPlugLabel(plug: String): String
}

object BotStringsEn : BotStrings {
    override fun startMenuTitle(version: String, rootStatus: String): String =
        "🤖 <b>Remote Bot Android</b> ($version)\n👑 <b>Root status:</b> $rootStatus\n📡 <b>Service:</b> 🟢 Active"
    override fun startMenuSubtitle(): String = "Select a command category:"
    override fun btnBasic(): String = "⚙️ Basic"
    override fun btnMedia(): String = "📷 Media"
    override fun btnComm(): String = "📞 Calls & SMS"
    override fun btnGeoSec(): String = "📍 Location"
    override fun btnFiles(): String = "📁 Files & Apps"
    override fun btnSystem(): String = "⚡ System & Root"
    override fun btnAutomation(): String = "🤖 Automation"
    override fun btnAllCommands(): String = "📋 All Commands"
    override fun btnStatus(): String = "📊 Status"
    override fun btnBack(): String = "🔙 Back to Menu"
    override fun btnToggles(): String = "🎛️ Toggles"
    override fun aboutTitle(version: String, rootStatus: String): String =
        """
        🤖 <b>Remote Bot Android</b>
        
        • <b>Version:</b> $version
        • <b>Build:</b> Clean Architecture (SDK 35 / Android 15)
        • <b>Root status:</b> $rootStatus
        
        🐙 <b>GitHub:</b> <a href="https://github.com/runalsh/remote-bot-android">github.com/runalsh/remote-bot-android</a>
        ✈️ <b>Author Telegram:</b> <a href="https://t.me/runalsh">@runalsh</a>
        
        ⚖️ License: Apache License 2.0
        """.trimIndent()

    override fun permissionsTitle(granted: Int, total: Int): String =
        "🛡️ <b>Android Permissions Status</b> ($granted/$total)"
    override fun filterLabel(filter: String): String = "Filter: <code>$filter</code>"
    override fun groupCriticalTitle(): String = "🔴 <b>Critical (System/Network/Background):</b>"
    override fun groupConfidentialTitle(): String = "🟡 <b>Confidential (Camera/Microphone/SMS):</b>"
    override fun groupStandardTitle(): String = "🟢 <b>Standard (Base features):</b>"
    override fun statusGranted(): String = "Granted"
    override fun statusDenied(): String = "Denied"
    override fun noPermissionsFound(): String = "No permissions found for filter."
    override fun permissionsUsageHint(): String = "💡 <i>Usage: /permissions [critical|confidential|standard|denied]</i>"

    override fun statusTitle(): String = "📊 <b>Remote Bot Status Summary</b>"
    override fun statusServiceRunning(): String = "🤖 <b>Service state:</b> 🟢 Active"
    override fun statusRoot(granted: Boolean): String =
        "👑 <b>Root (Superuser):</b> " + if (granted) "✅ Granted (UID 0)" else "❌ Denied"
    override fun statusConnectionMode(mode: String): String = "📡 <b>Connection mode:</b> <code>$mode</code>"
    override fun statusTelegramApiTitle(): String = "🌐 <b>Telegram Bot API:</b>"
    override fun statusActiveNode(status: String): String = "• <b>Active node:</b> $status"
    override fun statusStrategy(strategy: String): String = "• <b>Selection strategy:</b> <code>$strategy</code>"
    override fun statusEndpointsPool(): String = "• <b>Endpoints pool:</b>"

    override fun incomingSmsTitle(from: String, sim: String): String =
        "📩 <b>Incoming SMS</b> (SIM: $sim)\n<b>From:</b> <code>$from</code>"
    override fun incomingCallTitle(from: String, sim: String): String =
        "📞 <b>Incoming Call</b> (SIM: $sim)\n<b>From:</b> <code>$from</code>"
    override fun missedCallTitle(from: String, sim: String): String =
        "🔴 <b>Missed Call</b> (SIM: $sim)\n<b>From:</b> <code>$from</code>"
    override fun batteryChargingTitle(level: Int): String = "⚡ <b>Charger Connected</b> ($level%)"
    override fun batteryLowTitle(level: Int): String = "🪫 <b>Low Battery</b> ($level%)"
    override fun batteryLevel(level: Int): String = "Battery level: $level%"
    override fun actionApproved(): String = "✅ Approved"
    override fun actionRejected(): String = "❌ Rejected"
    override fun commandNotFound(cmd: String): String = "Unknown command: $cmd"

    override fun categoryBase(): String =
        """
        ⚙️ <b>Basic Commands & Diagnostics:</b>
        • /status — overall connection status, Telegram API ping, FCM/HMS
        • /permissions [filter] — permission audit (critical/confidential/standard/denied)
        • /ping — response latency test
        • /echo &lt;text&gt; — echo incoming command test
        • /chat_id — display current chat Chat ID and User ID
        • /about — bot build version, repo and contacts
        • /uptime — service process and Android system uptime
        """.trimIndent()

    override fun categoryMedia(): String =
        """
        📷 <b>Camera, Audio & Screen:</b>
        • /photo [front|back] — snapshot from front or rear camera
        • /videorec [front|back] [seconds] — covert video recording
        • /gallery [N] — send latest N media files from gallery
        • /rec [seconds] — covert voice audio recording from microphone
        • /rec_file [seconds] — audio recording saved as .wav/.m4a
        • /tts &lt;text&gt; — speech synthesizer playback via speaker
        • /player [play|pause|next|prev|stop] — media playback control
        • /volume [0-100|stream] [value] — volume control for audio streams
        • /ring_mode [normal|silent|vibrate] — ringer mode switch
        • /device_search — loud alarm siren and vibration to find lost phone
        • /screenshot — instant screenshot (MediaProjection / Root)
        • /screen_rec [seconds] — record screen to video file (Root / Service)
        """.trimIndent()

    override fun categoryComm(): String =
        """
        📞 <b>Calls, SMS & Notifications:</b>
        • /sms &lt;number&gt; &lt;text&gt; — send SMS with SIM selection
        • /sms_inbox [N] — view latest incoming SMS messages
        • /call &lt;number&gt; — initiate voice phone call
        • /callshistory [N] — call logs (incoming, outgoing, missed)
        • /ussd &lt;code&gt; — execute USSD balance or service request (Android 8.0+)
        • /notify [list|clear] — view active status bar notifications
        • /notify_send [id] — forward notification to Telegram chat
        • /fake_notify &lt;title&gt; | &lt;text&gt; — generate mock notification in status bar
        • /answer &lt;notif_id&gt; &lt;text&gt; — quick reply (RemoteInput) to messenger
        • /notify_clear [id|all] — dismiss notification from status bar
        • /notify_rule [add|del|list] — regex filter rules for notifications
        """.trimIndent()

    override fun categoryGeoSec(): String =
        """
        📍 <b>Location & Security:</b>
        • /location — current GPS/Network coordinates with Google Maps link
        • /track [start|stop|status] — continuous background GPS route tracking
        • /shake_detector [on|off|sens] — accelerometer motion alarm
        • /detectmotion [on|off|front|back] — camera motion detector with auto-photo
        • /sound_detector [on|off|sens] — sound/voice detector
        """.trimIndent()

    override fun categoryFiles(): String =
        """
        📁 <b>Files & Applications:</b>
        • /get_file &lt;path&gt; — download any file from Android filesystem
        • /download &lt;url&gt; [path] — remote download from internet to device
        • /ls &lt;path&gt; — list directory contents
        • /ls_interactive &lt;path&gt; — interactive file explorer with inline buttons
        • /apps [system|user|all] — list installed apps and packages
        • /app_launch &lt;package&gt; — launch selected app on screen
        • /install — remote install APK (silent install with Root)
        • /taskmanager (alias: /top) — running processes and RAM usage
        • /kill &lt;package|pid&gt; — force kill application (Root)
        """.trimIndent()

    override fun categorySystem(): String =
        """
        ⚡ <b>System & Root:</b>
        • /toggles (aliases: /panel, /quick_settings) — interactive toggle buttons
        • /system_info — device info summary, RAM, ROM, CPU, Root
        • /uptime — service and system uptime
        • /battery — battery status, level, temperature, voltage
        • /network — network interfaces, IP (IPv4/IPv6), DNS, MAC, MTU
        • /wifi [on|off|toggle] — Wi-Fi status and toggle
        • /bluetooth [on|off|toggle] — Bluetooth status and toggle
        • /flashlight [on|off|toggle] — flashlight (camera LED)
        • /airplane [on|off|toggle] — airplane mode ⚡
        • /hotspot [on|off|toggle] (alias: /tethering) — Wi-Fi Hotspot ⚡
        • /mobile_data [on|off|toggle] (alias: /data) — cellular mobile data ⚡
        • /gps [on|off|toggle] (alias: /location_mode) — GPS toggle ⚡
        • /powersave [on|off|toggle] (alias: /battery_saver) — battery saver mode ⚡
        • /auto_brightness [on|off|toggle] — auto brightness toggle
        • /clipboard [text|clear] — clipboard read and write
        • /sync [on|off|toggle] — account auto-sync toggle
        • /adb [on|off|toggle] (alias: /usb_debugging) — USB debugging (ADB) ⚡
        • /adb_wifi [on|off|toggle|port] — wireless ADB debugging ⚡ (port 5555)
        • /shell &lt;cmd&gt; (alias: /sh) — Linux terminal shell (Root/sh) ⚡
        • /reboot — reboot device ⚡ (10s delay)
        • /shutdown (alias: /poweroff) — power off device ⚡ (10s delay)
        • /recovery — reboot into Recovery ⚡ (10s delay)
        • /fastboot (alias: /bootloader) — reboot into Bootloader ⚡ (10s delay)

        <i>⚡ = requires Root (Superuser) permissions</i>
        """.trimIndent()

    override fun categoryAuto(): String =
        """
        🤖 <b>Automation, Tasker & Network:</b>
        • /tasker &lt;TaskName&gt; [par1] [par2] — launch Tasker tasks
        • /script &lt;code&gt; — execute system shell script
        • /timers [list|add|del] — scheduled & recurring command scheduler
        • /proxy — view proxy status and help
        • /proxy [socks5|http|wss|off] — quick switch or disable proxy
        • /active — active background operations
        • /stop_all — emergency cancel of all operations
        • /log [N] [level] — event log (default 20, max 100, DEBUG/INFO/WARN/ERROR)
        • /backup — export config backup to JSON file
        • /restore_backup [json|file] — restore config from text or file
        • /unsent_files — unsent files queue
        • /bot_status — Telegram and SMS channel status
        • /tg_bot [on|off|toggle] — enable/disable Telegram bot
        • /sms_bot [on|off|toggle] — SMS bot status and switch
        • /bot_toggle &lt;tg|sms&gt; [on|off] — universal communication channel toggle
        • /add_user [id] / /del_user &lt;id&gt; / /list_users — Telegram User ID whitelist
        • /add_phone [num] / /del_phone &lt;num&gt; / /list_phones — trusted SMS numbers without PIN
        • /add_forward [num] / /del_forward &lt;num&gt; / /list_forwards — forward numbers
        • /tg_forward [sms|calls|push|battery_charge|battery_low|battery] [on|off] — event forwarding
        • /events [on|off|notif|sms|calls|system|list|id] (alias: /subs) — subscriptions menu
        • /whitelist_user [add|del|clear|list|&lt;id&gt;] (alias: /user) — user whitelist management
        • /whitelist_phone [add|del|clear|list|&lt;num&gt;] (alias: /phone) — phone whitelist management
        • /sms_forward [add|del|clear|list|on|off|missed|push|translit|&lt;num&gt;] — forwarding management
        """.trimIndent()

    override fun fullHelp(version: String, rootText: String): String =
        """
        🤖 <b>Remote Bot Android</b> ($version)
        👑 Root status: $rootText

        <b>${categoryBase()}</b>

        <b>${categoryMedia()}</b>

        <b>${categoryComm()}</b>

        <b>${categoryGeoSec()}</b>

        <b>${categoryFiles()}</b>

        <b>${categorySystem()}</b>

        <b>${categoryAuto()}</b>
        """.trimIndent()
    override fun radioUnknownCmd(cmd: String): String = "Unknown radio command: $cmd"
    override fun radioWifiNotFound(): String = "Wi-Fi adapter not found on this device."
    override fun radioBtNotFound(): String = "Bluetooth adapter not found on this device."
    override fun radioCamServiceUnavailable(): String = "Camera service unavailable."
    override fun radioNoFlashCamera(): String = "No camera with flash found."
    override fun radioTorchFail(e: String): String = "Flashlight control failed: $e"
    override fun radioBtOnFail(): String = "⚠️ Failed to turn Bluetooth on (Root or permission required)."
    override fun radioBtOffFail(): String = "⚠️ Failed to turn Bluetooth off."
    override fun radioBtToggleFail(): String = "⚠️ Failed to toggle Bluetooth."
    override fun radioInvalidParam(u: String): String = "Invalid parameter. Use: <code>$u</code>"
    override fun radioUsage(u: String): String = "Use: <code>$u</code>"
    override fun radioAirplaneRootFail(): String = "❌ Failed to toggle airplane mode via Root shell."
    override fun radioHotspotStartFail(): String = "❌ Failed to start hotspot."
    override fun radioHotspotStopFail(): String = "❌ Failed to stop hotspot."
    override fun radioHotspotToggleFail(): String = "❌ Failed to toggle hotspot."
    override fun radioDataOnFail(): String = "❌ Failed to enable mobile data."
    override fun radioDataOffFail(): String = "❌ Failed to disable mobile data."
    override fun radioDataToggleFail(): String = "❌ Failed to toggle mobile data."
    override fun radioGpsOnFail(): String = "❌ Failed to enable GPS."
    override fun radioGpsOffFail(): String = "❌ Failed to disable GPS."
    override fun radioGpsToggleFail(): String = "❌ Failed to toggle GPS."
    override fun radioStateOnG(): String = "🟢 On"
    override fun radioStateOffR(): String = "🔴 Off"
    override fun radioStateOffW(): String = "⚪ Off"
    override fun radioStateAirplaneOn(): String = "✈️ On"
    override fun radioStateHotspotOn(): String = "📡 On"
    override fun radioStateHotspotOff(): String = "⚪ Off"
    override fun radioStateDataOn(): String = "🟢 On"
    override fun radioStateDataOff(): String = "🔴 Off"
    override fun radioStateGpsOn(): String = "📍 On"
    override fun radioStateGpsOff(): String = "⚪ Off"
    override fun radioNotConnected(): String = "Not connected"
    override fun radioNoPerm(): String = "No permission"
    override fun radioWordOn(): String = "on"
    override fun radioWordOff(): String = "off"
    override fun radioWordOnF(): String = "on"
    override fun radioWordOffF(): String = "off"
    override fun radioWifiStatus(state: String, ssid: String, rssi: Int, speed: String, ip: String): String = "📶 <b>Wi-Fi status:</b> $state\n• Network (SSID): <code>$ssid</code>\n• Signal (RSSI): <code>$rssi dBm</code>\n• Link speed: <code>$speed</code>\n• Local IP: <code>$ip</code>"
    override fun radioBtStatus(state: String, name: String, bonded: Int): String = "🦷 <b>Bluetooth status:</b> $state\n• Adapter name: <code>$name</code>\n• Paired devices: <code>$bonded</code>"
    override fun radioWifiOnOk(): String = "✅ <b>Wi-Fi turned on!</b>"
    override fun radioWifiOnFailLong(): String = "⚠️ Failed to turn Wi-Fi on.\nOn Android 10+ direct control requires Root (libsu)."
    override fun radioWifiOffOk(): String = "🔴 <b>Wi-Fi turned off!</b>"
    override fun radioWifiOffFailLong(): String = "⚠️ Failed to turn Wi-Fi off.\nOn Android 10+ direct control requires Root (libsu)."
    override fun radioWifiToggled(label: String): String = "🔄 <b>Wi-Fi toggled: $label</b>"
    override fun radioWifiToggleFailLong(): String = "⚠️ Failed to toggle Wi-Fi.\nOn Android 10+ direct control requires Root (libsu)."
    override fun radioBtOnOk(): String = "✅ <b>Bluetooth turned on!</b>"
    override fun radioBtOffOk(): String = "🔴 <b>Bluetooth turned off!</b>"
    override fun radioBtToggled(label: String): String = "🔄 <b>Bluetooth toggled: $label</b>"
    override fun radioTorchState(label: String): String = "🔦 <b>Flashlight:</b> $label"
    override fun radioTorchOnOk(): String = "🔦 <b>Flashlight on!</b>"
    override fun radioTorchOffOk(): String = "💡 <b>Flashlight off!</b>"
    override fun radioTorchToggled(label: String): String = "🔄 <b>Flashlight $label</b>"
    override fun radioRootActive(): String = "🟢 Root active"
    override fun radioRootNote(): String = "🔒 Root required to change"
    override fun radioAirplaneStatus(state: String, note: String): String = "✈️ <b>Airplane mode:</b> $state\n<i>$note</i>"
    override fun radioNeedRootAirplane(): String = "⚠️ Airplane mode requires Root (Superuser)!\nGrant Root in the app on the Diagnostics tab."
    override fun radioAirplaneOnOk(): String = "✈️ <b>Airplane mode on!</b>"
    override fun radioAirplaneOffOk(): String = "🌐 <b>Airplane mode off!</b>"
    override fun radioAirplaneToggled(label: String): String = "🔄 <b>Airplane mode $label</b>"
    override fun radioHotspotStatus(state: String, note: String): String = "📡 <b>Wi-Fi Hotspot:</b> $state\n<i>$note</i>"
    override fun radioNeedRootHotspot(): String = "⚠️ Hotspot requires Root (Superuser)!\nGrant Root in the app on the Diagnostics tab."
    override fun radioHotspotOnOk(): String = "📡 <b>Hotspot on!</b>"
    override fun radioHotspotOffOk(): String = "⚪ <b>Hotspot off!</b>"
    override fun radioHotspotToggled(label: String): String = "🔄 <b>Hotspot $label</b>"
    override fun radioDataStatus(state: String, note: String): String = "📶 <b>Mobile data:</b> $state\n<i>$note</i>"
    override fun radioNeedRootData(): String = "⚠️ Toggling mobile data requires Root (Superuser)!\nGrant Root in the app on the Diagnostics tab."
    override fun radioDataOnOk(): String = "🟢 <b>Mobile data on!</b>"
    override fun radioDataOffOk(): String = "🔴 <b>Mobile data off!</b>"
    override fun radioDataToggled(label: String): String = "🔄 <b>Mobile data $label</b>"
    override fun radioGpsStatus(state: String, note: String): String = "📍 <b>Location (GPS):</b> $state\n<i>$note</i>"
    override fun radioNeedRootGps(): String = "⚠️ GPS control requires Root (Superuser)!\nGrant Root in the app on the Diagnostics tab."
    override fun radioGpsOnOk(): String = "📍 <b>Location on!</b>"
    override fun radioGpsOffOk(): String = "⚪ <b>Location off!</b>"
    override fun radioGpsToggled(label: String): String = "🔄 <b>Location $label</b>"
    override fun botLanguageCurrent(cur: String): String = "🌐 Current bot language: <code>$cur</code>\nChange with: <code>/bot_language auto|en|ru</code>"
    override fun botLanguageSet(v: String): String = "✅ Bot language switched to: <code>$v</code>"
    override fun botLanguageInvalid(v: String): String = "⚠️ Unknown language '$v'. Use: auto, en, ru"
    override fun qsPanelBase(hasRoot: Boolean, wifiOn: Boolean, btOn: Boolean, flashOn: Boolean, autoBrightnessOn: Boolean, syncOn: Boolean): String = run { val _h = if (hasRoot) "🟢 Granted (UID 0)" else "⚪ Normal mode (no Root)"; val _w = if (wifiOn) "🟢 ON" else "⚪ OFF"; val _b = if (btOn) "🟢 ON" else "⚪ OFF"; val _f = if (flashOn) "🟢 ON" else "⚪ OFF"; val _a = if (autoBrightnessOn) "☀️ ON" else "⚪ OFF"; val _s = if (syncOn) "🔄 ON" else "⚪ OFF"; "🎛 <b>Quick Settings (Toggles Panel)</b>\n<i>Superuser status: $_h</i>\n\n• Wi-Fi: $_w\n• Bluetooth: $_b\n• Flashlight: $_f\n• Auto Brightness: $_a\n• Sync: $_s\n" }
    override fun qsPanelRoot(airplaneOn: Boolean, hotspotOn: Boolean, mobileDataOn: Boolean, gpsOn: Boolean, powerSaveOn: Boolean, adbOn: Boolean, adbWifiOn: Boolean, localIp: String, adbWifiPort: Int): String = run { val _a = if (airplaneOn) "✈️ ON" else "⚪ OFF"; val _ho = if (hotspotOn) "📡 ON" else "⚪ OFF"; val _m = if (mobileDataOn) "📶 ON" else "⚪ OFF"; val _g = if (gpsOn) "📍 ON" else "⚪ OFF"; val _p = if (powerSaveOn) "🔋 ON" else "⚪ OFF"; val _u = if (adbOn) "🔌 ON" else "⚪ OFF"; val _x = if (adbWifiOn) "📶 ON (<code>$localIp:$adbWifiPort</code>)" else "⚪ OFF"; "• Airplane Mode: $_a ⚡\n• Hotspot: $_ho ⚡\n• Mobile Data: $_m ⚡\n• GPS: $_g ⚡\n• Battery Saver: $_p ⚡\n• USB Debugging (ADB): $_u ⚡\n• Wireless ADB: $_x ⚡\n" }
    override fun qsUnknownCmd(cmd: String): String = "Unknown system settings command: $cmd"
    override fun qsOnWord(): String = "ON"
    override fun qsOffWord(): String = "OFF"
    override fun ipNotFound(): String = "IP_NOT_FOUND"
    override fun qsBtnTorch(on: Boolean): String = if (on) "🔦 Torch [ON]" else "🔦 Torch [OFF]"
    override fun qsBtnSync(on: Boolean): String = if (on) "🔄 Sync [ON]" else "🔄 Sync [OFF]"
    override fun qsBtnAirplane(on: Boolean): String = if (on) "✈️ Plane [ON] ⚡" else "✈️ Plane [OFF] ⚡"
    override fun qsBtnData(on: Boolean): String = if (on) "📱 Data [ON] ⚡" else "📱 Data [OFF] ⚡"
    override fun qsBtnSaver(on: Boolean): String = if (on) "🔋 Saver [ON] ⚡" else "🔋 Saver [OFF] ⚡"
    override fun qsBtnBright(on: Boolean): String = if (on) "☀️ Bright [ON]" else "☀️ Bright [OFF]"
    override fun qsBtnRefresh(): String = "🔄 Refresh Panel"
    override fun qsRootTip(): String = "\n<i>💡 Grant Root in the app to unlock toggles: Airplane mode, Hotspot, Mobile data, GPS, Battery saver, USB/Wi-Fi Debugging.</i>\n"
    override fun psStateOn(): String = "🔋 On"
    override fun psStatus(state: String, note: String): String = "🔋 <b>Battery saver mode:</b> $state\n<i>$note</i>"
    override fun psNeedRoot(): String = "⚠️ Battery saver control requires Root (Superuser)!\nGrant Root in the app on the Diagnostics tab."
    override fun psOnOk(): String = "🔋 <b>Battery saver on!</b>"
    override fun psOnFail(): String = "❌ Failed to enable battery saver."
    override fun psOffOk(): String = "⚪ <b>Battery saver off!</b>"
    override fun psOffFail(): String = "❌ Failed to disable battery saver."
    override fun psToggled(label: String): String = "🔄 <b>Battery saver $label</b>"
    override fun psToggleFail(): String = "❌ Failed to toggle battery saver."
    override fun abStateOn(): String = "☀️ On"
    override fun abStateOff(): String = "⚪ Off (manual)"
    override fun abStatus(state: String): String = "☀️ <b>Auto brightness:</b> $state"
    override fun abOnOk(): String = "☀️ <b>Auto brightness on!</b>"
    override fun abOnFail(): String = "⚠️ Failed to enable auto brightness.\nRequires system settings permission or Root."
    override fun abOffOk(): String = "⚪ <b>Auto brightness off (manual)!</b>"
    override fun abOffFail(): String = "⚠️ Failed to disable auto brightness.\nRequires system settings permission or Root."
    override fun abToggled(label: String): String = "🔄 <b>Auto brightness $label</b>"
    override fun abToggleFail(): String = "⚠️ Failed to toggle auto brightness.\nRequires system settings permission or Root."
    override fun syncStatus(state: String): String = "🔄 <b>Accounts auto-sync:</b> $state"
    override fun syncOnOk(): String = "✅ <b>Accounts auto-sync on!</b>"
    override fun syncOffOk(): String = "🔴 <b>Accounts auto-sync off!</b>"
    override fun syncToggled(label: String): String = "🔄 <b>Accounts auto-sync $label!</b>"
    override fun cbUnavailable(): String = "Clipboard is unavailable."
    override fun cbEmpty(): String = "📋 <b>Clipboard is empty.</b>"
    override fun cbClearBtn(): String = "🗑 Clear clipboard"
    override fun cbContent(t: String): String = "📋 <b>Clipboard content:</b>\n<code>$t</code>"
    override fun cbSet(t: String): String = "📋 <b>Text copied to device clipboard:</b>\n<code>$t</code>"
    override fun adbNeedRoot(): String = "❌ <b>Superuser (Root) permission required!</b>\nUSB debugging (ADB) control is not possible without root.\nGrant access in the app on the Diagnostics tab."
    override fun adbStatus(state: String, flag: Int): String = "🔌 <b>USB Debugging (ADB):</b> $state\n• Access: <code>Root (Superuser)</code>\n• Global setting: <code>adb_enabled = $flag</code>"
    override fun adbOnOk(): String = "✅ <b>USB debugging (ADB) on!</b> ⚡"
    override fun adbOffOk(): String = "🔴 <b>USB debugging (ADB) off.</b> ⚡"
    override fun adbUsage(): String = "Usage: <code>/adb [status|on|off|toggle]</code>"
    override fun awifiNeedRoot(): String = "❌ <b>Superuser (Root) permission required!</b>\nEnabling Wi-Fi debugging at system level requires root.\nGrant access in the app on the Diagnostics tab."
    override fun awifiStateOn(port: Int): String = "🟢 On (port $port)"
    override fun awifiStatusOn(state: String, ip: String, port: Int): String = "📶 <b>Wireless ADB (Wi-Fi debugging):</b> $state\n• Local IP: <code>$ip</code>\n• Port: <code>$port</code>\n\n💡 <b>Connect from PC:</b>\n<code>adb connect $ip:$port</code>\n\n💡 <b>Disconnect:</b>\n<code>adb disconnect $ip:$port</code>"
    override fun awifiStatusOff(state: String, ip: String): String = "📶 <b>Wireless ADB (Wi-Fi debugging):</b> $state\n• Default port: <code>5555</code>\n• Current IP: <code>$ip</code>\n\nUse the button below or <code>/adb_wifi on</code> to start ADB over TCP/IP."
    override fun awifiOnOkA(port: Int, ip: String): String = "✅ <b>Wi-Fi debugging enabled!</b> ⚡\n• Port: <code>$port</code>\n• Device IP: <code>$ip</code>\n\n💡 <b>To connect from a computer run:</b>\n<code>adb connect $ip:$port</code>"
    override fun awifiOnOkB(port: Int, ip: String): String = "✅ <b>Wi-Fi debugging enabled!</b> ⚡\n• Port: <code>$port</code>\n• IP: <code>$ip</code>\n\n💡 <b>Connect:</b> <code>adb connect $ip:$port</code>"
    override fun awifiUsage(): String = "Usage: <code>/adb_wifi [status|on|off|toggle|port]</code>\nExample: <code>/adb_wifi on 5555</code>"
    override fun auUnknownCmd(cmd: String): String = "Unknown audio command: /$cmd"
    override fun auRecStopped(): String = "⏹ Microphone recording stopped. Saving audio is being sent..."
    override fun auNoActiveRec(): String = "⚠️ No active microphone recording to stop."
    override fun auRecGoing(name: String): String = "🔴 <b>Recording:</b> $name"
    override fun auRecIdle(): String = "⏹ <b>Idle</b> (no active recording)"
    override fun auRecPanel(status: String): String = "🎙 <b>Hidden microphone recording (/rec):</b>\n• Status: $status\n• Choose duration with the buttons below (up to 1 hour) or start a long recording with the option to stop at any moment."
    override fun auRecOpName(dur: String): String = "🎙 Microphone recording ($dur)"
    override fun auRecDescFile(dur: String): String = "As a file (up to $dur)"
    override fun auRecDescVoice(dur: String): String = "Voice message (up to $dur)"
    override fun auRecCaptionFile(sec: Int): String = "🎙 <b>Microphone recording</b> (${sec}s)"
    override fun auRecCaptionVoice(sec: Int): String = "🎙 <b>Microphone recording</b> (${sec}s)"
    override fun auRecError(e: String): String = "Audio recording error: $e"
    override fun auDurSec(s: Int): String = "${s}s"
    override fun auDurMin(m: Int): String = "${m}min"
    override fun auDurMinSec(m: Int, s: Int): String = "${m}m ${s}s"
    override fun auBtnSec(n: String): String = "⏱ $n sec"
    override fun auBtnSecMin(n: String, m: String): String = "⏱ $n sec ($m)"
    override fun auBtnRecUntilStop(): String = "⏹ Record until stopped"
    override fun auBtnStopRec(): String = "⏹ Stop recording"
    override fun auBtnRefreshPanel(): String = "🔄 Refresh panel"
    override fun auBtnToVoice(): String = "🔄 Switch to Voice"
    override fun auBtnToFile(): String = "🔄 Switch to File"
    override fun auTtsUsage(): String = "🗣 <b>Speech synthesis (TTS)</b>\nUsage: <code>/tts [language] &lt;text&gt;</code>\nExamples:\n• <code>/tts Hello, the phone is online!</code>\n• <code>/tts en Hello</code>"
    override fun auTtsPlayed(t: String): String = "🗣 Played through the speaker:\n<i>«$t»</i>"
    override fun auTtsError(e: String): String = "TTS error: $e"
    override fun auBtnSirenOn(): String = "🔊 Alarm siren"
    override fun auBtnSirenOff(): String = "⏹ Turn off siren"
    override fun auBtnRingNormal(): String = "🔔 Profile: Ringtone"
    override fun auBtnSilent(): String = "🔕 Silent"
    override fun auBtnVibrate(): String = "📳 Vibrate"
    override fun auBtnVolumeLevels(): String = "📊 Volume levels"
    override fun auBtnRefresh(): String = "🔄 Refresh"
    override fun auPlayerHead(mode: String): String = "🎵 <b>Media player & Sound</b>\n• Current sound profile: <b>$mode</b>\n• Volume control: <code>/volume [stream] [level]</code>\n• Speech synthesis: <code>/tts &lt;text&gt;</code>"
    override fun auVolHead(): String = "🔊 <b>Device volume levels</b>:\n\n"
    override fun auStreamRing(): String = "🔔 Ringtone (Ring)"
    override fun auStreamNotif(): String = "💬 Notifications"
    override fun auStreamAlarm(): String = "⏰ Alarm"
    override fun auStreamMedia(): String = "🎵 Media"
    override fun auStreamCall(): String = "📞 Voice Call"
    override fun auStreamSystem(): String = "⚙️ System"
    override fun auVolHint(): String = "\n<i>Set with: <code>/volume &lt;media|ring|alarm|notif&gt; &lt;0..15&gt;</code></i>"
    override fun auBtnMedia50(): String = "🎵 Media 50%"
    override fun auBtnMedia100(): String = "🎵 Media 100%"
    override fun auBtnRing100(): String = "🔔 Ringtone 100%"
    override fun auBtnAlarm100(): String = "⏰ Alarm 100%"
    override fun auVolSet(name: String, lvl: String, max: String): String = "🔊 Volume of stream <b>$name</b> set to: <code>$lvl/$max</code>"
    override fun auVolError(e: String): String = "Volume set error: $e"
    override fun auRingHead(mode: String): String = "🔔 <b>Device sound profile</b>: <b>$mode</b>\n\n• <code>/ring_mode normal</code> — normal mode with sound\n• <code>/ring_mode silent</code> — silent (DND / Quiet)\n• <code>/ring_mode vibrate</code> — vibration only"
    override fun auBtnNormal(): String = "🔔 Normal"
    override fun auRingToggled(icon: String, mode: String): String = "$icon Sound profile switched to: <b>$mode</b>"
    override fun auRingError(e: String): String = "Failed to change sound mode (requires Do Not Disturb access): $e"
    override fun auSirenOff(): String = "⏹ <b>Device search</b>: siren is off."
    override fun auSirenOp(): String = "🚨 Device search siren"
    override fun auSirenOpDesc(): String = "Alarm volume 100%"
    override fun auSirenOn(): String = "🚨 <b>Device search siren is ON!</b>\nAlarm volume set to 100%."
    override fun auSirenError(e: String): String = "Siren start error: $e"
    override fun auSdUsage(): String = "Format: <code>/sound_detector</code> (interactive panel), <code>/sound_detector &lt;1..10&gt;</code> or <code>/sound_detector off</code>"
    override fun auSdOpName(lvl: String): String = "🔊 Sound detector (Microphone, $lvl/10)"
    override fun auSdOpDesc(db: String): String = "Threshold $db dB"
    override fun auSdActive(db: String): String = "✅ <b>ACTIVE</b> (Threshold: <b>$db dB</b>)"
    override fun auSdOff(): String = "❌ <b>DISABLED</b>"
    override fun auBtnSdOff(): String = "⏹ Turn off"
    override fun auBtnSdOn(lvl: String): String = "▶️ Turn on ($lvl/10)"
    override fun auSdPanel(status: String, lvl: String, db: String): String = "🔊 <b>Sound guard detector (Microphone)</b>\n• Status: $status\n• Current level: <b>$lvl / 10</b> (Threshold: $db dB)\n\n<i>Scale: 1 = loud noise (85 dB, shouts/claps), 10 = ultrasensitive (40 dB, quiet whisper/rustles).</i>\nChoose sensitivity with the buttons below:"
    override fun auB60(): String = "⏱ 60 sec (1m)"
    override fun auB300(): String = "⏱ 300 sec (5m)"
    override fun auB600(): String = "⏱ 600 sec (10m)"
    override fun auB1800(): String = "⏱ 1800 sec (30m)"
    override fun auB3600(): String = "⏱ 3600 sec (1 hour)"
    override fun secUnknownCmd(cmd: String): String = "Unknown security command: $cmd"
    override fun secUserListEmpty(): String = "<i>List is empty (access granted to everyone or to the first connected user only)</i>"
    override fun secUserListBody(listStr: String): String = "👥 <b>Telegram Users Whitelist (User ID):</b>\n\n$listStr\n\n<i>Control:</i>\n<code>/add_user [id]</code> — add user (default: your ID)\n<code>/del_user &lt;id&gt;</code> — remove user\n<code>/whitelist_user clear</code> — clear list"
    override fun secUserListCleared(): String = "⚠️ Telegram users whitelist fully cleared."
    override fun secAddUserIdHint(): String = "Provide numeric User ID: <code>/add_user 123456789</code>"
    override fun secUserExists(id: String): String = "ℹ️ User <code>$id</code> is already in the whitelist."
    override fun secUserAdded(id: String): String = "✅ User <code>$id</code> added to the Telegram whitelist."
    override fun secDelUserIdHint(): String = "Provide numeric User ID: <code>/del_user 123456789</code>"
    override fun secUserNotFound(id: String): String = "User <code>$id</code> not found in the list."
    override fun secUserDeleted(id: String): String = "🗑 User <code>$id</code> removed from the whitelist."
    override fun secAddDelUserFmt(): String = "Format: <code>/add_user &lt;id&gt;</code> or <code>/del_user &lt;id&gt;</code>"
    override fun secUserIdTgHint(): String = "Provide numeric Telegram User ID: <code>/add_user 123456789</code>"
    override fun secUserExistsTg(id: String): String = "ℹ️ User <code>$id</code> is already in the Telegram whitelist."
    override fun secUserNotFoundTg(id: String): String = "User <code>$id</code> not found in the whitelist."
    override fun secUserDeletedTg(id: String): String = "🗑 User <code>$id</code> removed from the Telegram whitelist."
    override fun secPhoneListEmpty(): String = "<i>List is empty (PIN-less control disabled)</i>"
    override fun secPhoneListBody(listStr: String): String = "📱 <b>Trusted SMS-control numbers (no PIN):</b>\n\n$listStr\n\n<i>Control:</i>\n<code>/add_phone [number]</code> — add number (defaults to your SMS number)\n<code>/del_phone &lt;number&gt;</code> — remove number\n<code>/whitelist_phone clear</code> — clear list"
    override fun secPhoneListCleared(): String = "⚠️ Trusted SMS-control list cleared."
    override fun secAddPhoneHint(): String = "Provide number: <code>/add_phone +79991234567</code>"
    override fun secPhoneExists(phone: String): String = "ℹ️ Number <code>$phone</code> is already in the trusted SMS list."
    override fun secPhoneAdded(phone: String): String = "✅ Number <code>$phone</code> added to trusted SMS-control list."
    override fun secDelPhoneHint(): String = "Provide number: <code>/del_phone +79991234567</code>"
    override fun secPhoneNotFound(phone: String): String = "Number <code>$phone</code> not found in the trusted list."
    override fun secPhoneDeleted(phone: String): String = "🗑 Number <code>$phone</code> removed from trusted SMS list."
    override fun secAddPhonePhoneHint(): String = "Provide phone number: <code>/add_phone +79991234567</code>"
    override fun secPhoneNoPinAdded(phone: String): String = "✅ Number <code>$phone</code> added to trusted SMS-control list (no PIN)."
    override fun secDelPhonePhoneHint(): String = "Provide phone number: <code>/del_phone +79991234567</code>"
    override fun secFwdListEmpty(): String = "<i>Target numbers list is empty (forwarding disabled)</i>"
    override fun secOnOff(on: Boolean): String = if (on) "On ✅" else "Off ❌"
    override fun secSmsFwdOn(): String = "✅ Incoming SMS forwarding over cellular network enabled."
    override fun secSmsFwdOff(): String = "⏹ Incoming SMS forwarding disabled."
    override fun secMissedFwd(enable: Boolean): String = "Missed calls SMS forwarding: ${if (enable) "enabled ✅" else "disabled ⏹"}"
    override fun secPushFwd(enable: Boolean): String = "Push notification SMS forwarding: ${if (enable) "enabled ✅" else "disabled ⏹"}"
    override fun secChargeFwd(enable: Boolean): String = "Battery charging status SMS forwarding: ${if (enable) "enabled ✅" else "disabled ⏹"}"
    override fun secLowBattFwd(enable: Boolean): String = "Low battery (≤20% every 3%) SMS forwarding: ${if (enable) "enabled ✅" else "disabled ⏹"}"
    override fun secAllBattFwd(enable: Boolean): String = "All battery alerts by SMS: ${if (enable) "enabled ✅" else "disabled ⏹"}"
    override fun secTranslitFwd(enable: Boolean): String = "SMS transliteration (GOST Latin): ${if (enable) "enabled ✅" else "disabled ⏹"}"
    override fun secAddFwdHint(): String = "Provide number: <code>/add_forward +79991234567</code>"
    override fun secFwdExists(phone: String): String = "ℹ️ Number <code>$phone</code> is already in the SMS forwarding list."
    override fun secFwdAdded(phone: String): String = "✅ Number <code>$phone</code> added to SMS forwarding list. Forwarding activated!"
    override fun secDelFwdHint(): String = "Provide number: <code>/del_forward +79991234567</code>"
    override fun secFwdNotFound(phone: String): String = "Number <code>$phone</code> not found in the forwarding list."
    override fun secFwdDeleted(phone: String): String = "🗑 Number <code>$phone</code> removed from the forwarding list."
    override fun secFwdCleared(): String = "⚠️ SMS forwarding numbers list cleared."
    override fun secFwdAddedShort(phone: String): String = "✅ Number <code>$phone</code> added for SMS forwarding!"
    override fun secAddFwdPhoneHint(): String = "Provide phone number: <code>/add_forward +79991234567</code>"
    override fun secFwdAddedCalls(phone: String): String = "✅ Number <code>$phone</code> added to SMS/calls forwarding list. Forwarding activated!"
    override fun secDelFwdPhoneHint(): String = "Provide phone number: <code>/del_forward +79991234567</code>"
    override fun secEventsEmpty(text: String): String = "ℹ️ Users whitelist is empty. Your subscriptions menu:\n\n$text"
    override fun secSubsHead(): String = "📋 <b>Per-user event delivery status:</b>\n\n"
    override fun secSelfTag(): String = " (You)"
    override fun secRoleCmds(): String = "🕹 <i>Commands only</i>"
    override fun secRoleRecv(): String = "📬 <i>Receiver</i>"
    override fun secEventsHint(): String = "<i>Control: <code>/events [id]</code> or <code>/events &lt;on|off&gt;</code></i>"
    override fun secUnknownCategory(c: String): String = "Unknown category: $c. Available: notif, sms, calls, system, on, off"
    override fun secStateLabel(on: Boolean): String = if (on) "enabled 🟢" else "disabled 🔴"
    override fun secEventsOffSelf(id: String): String = "🔕 Event delivery for your account (<code>$id</code>) <b>disabled</b>.\nYou are switched to <b>Commands only</b> role (all commands available, background spam off)."
    override fun secEventsOnSelf(id: String): String = "🔔 Main event delivery for your account (<code>$id</code>) <b>enabled</b> (push notifications, SMS, calls)."
    override fun secEventsOffUser(id: String): String = "🔕 Event delivery for user <code>$id</code> disabled (Commands-only mode)."
    override fun secEventsOnUser(id: String): String = "🔔 Event delivery for user <code>$id</code> enabled."
    override fun secCategoryForUser(c: String, id: String, st: String): String = "✅ Category <b>$c</b> for <code>$id</code> $st."
    override fun secCategorySelf(c: String, st: String): String = "✅ Category <b>$c</b> for your account $st."
    override fun secEventsUsage(): String = "Command format:\n• <code>/events</code> — open interactive menu\n• <code>/events list</code> — user subscriptions list\n• <code>/events &lt;on|off&gt;</code> — enable/disable all\n• <code>/events &lt;notif|sms|calls|system&gt; &lt;on|off&gt;</code> — toggle category\n• <code>/events &lt;user_id&gt; [on|off]</code> — configure specific user"
    override fun secFwdListBody(listStr: String, s1: String, s2: String, s3: String, s4: String, s5: String, s6: String): String = "📤 <b>SMS and Notifications forwarding numbers:</b>\n\n$listStr\n\n• SMS forwarding: $s1\n• Missed calls forwarding: $s2\n• Push forwarding: $s3\n• Charging/power status: $s4\n• Low battery (≤20% every 3%): $s5\n• Transliteration: $s6\n\n<i>Control:</i>\n<code>/add_forward [number]</code> — add destination number\n<code>/del_forward &lt;number&gt;</code> — remove destination number\n<code>/sms_forward on|off</code> — toggle SMS forwarding\n<code>/sms_forward missed on|off</code> — toggle missed calls forwarding\n<code>/sms_forward push on|off</code> — toggle Push forwarding\n<code>/sms_forward battery_charge on|off</code> — toggle charging status\n<code>/sms_forward battery_low on|off</code> — toggle low battery (≤20% every 3%)\n<code>/sms_forward battery on|off</code> — toggle all battery alerts\n<code>/sms_forward translit on|off</code> — toggle transliteration\n<code>/sms_forward clear</code> — clear all numbers"
    override fun secDelUserIdTgHint(): String = "Provide numeric Telegram User ID: <code>/del_user 123456789</code>"
    override fun secPhoneNotFoundTg(phone: String): String = "Number <code>$phone</code> not found in the trusted SMS list."
    override fun locUnknownCmd(cmd: String): String = "Unknown location command: $cmd"
    override fun locAcquiring(t: String): String = "🛰️ Determining location... This may take up to $t seconds."
    override fun locOpName(t: String): String = "🛰️ Location search (${t}s)"
    override fun locOpDesc(t: String): String = "GPS/LBS coordinates acquisition (timeout ${t}s)"
    override fun locAcquireError(e: String): String = "❌ Coordinate acquisition error: $e"
    override fun locTrackInactive(): String = "ℹ️ Periodic tracking is not active right now."
    override fun locTrackOpName(r: String): String = "🛰️ Periodic GPS tracking ($r)"
    override fun locTrackOpDesc(r: String): String = "Interval $r"
    override fun locTrackPanelState(act: Boolean): String = if (act) "🟢 <b>ACTIVE</b>" else "⏹ <b>STOPPED</b>"
    override fun locTrackPanelStateInt(act: Boolean, i: String): String = if (act) "🟢 <b>ACTIVE</b> ($i)" else "⏹ <b>STOPPED</b>"
    override fun locDurSec(s: Long): String = "${s} sec"
    override fun locDurHour(h: Long): String = "${h} h"
    override fun locDurMin(m: Long): String = "${m} min"
    override fun locDurMinSec(m: Long, s: Long): String = "${m} min ${s} sec"
    override fun locStateGps(): String = "🛰️ Satellite GPS/GLONASS"
    override fun locStateLbs(): String = "📡 Indirect evidence (Cell towers / Wi-Fi)"
    override fun locSignalDefault(): String = "Reliable reception"
    override fun locSignalGps(lvl: String): String = "📶 <b>GPS signal:</b> $lvl"
    override fun locSignalGpsSats(used: String, total: String): String = "📶 <b>GPS signal:</b> $used of $total satellites in fix"
    override fun locSignalGpsLabel(): String = "📶 <b>GPS signal:</b> "
    override fun locSignalNoGps(): String = "❌ No satellite signal (using LBS/Wi-Fi)"
    override fun locSeqStr(n: String): String = " [Fix #$n]"
    override fun locTitleTracked(seq: String): String = "🛰️ <b>Periodic location tracking$seq:</b>"
    override fun locTitleDevice(): String = "📍 <b>Device location:</b>"
    override fun locAltitudeSpeed(alt: String, spd: String): String = "⛰️ <b>Altitude:</b> $alt m | 🏃 <b>Speed:</b> $spd km/h"
    override fun locCoord(lat: String, lon: String): String = "🌐 <b>Coordinates:</b> <code>$lat, $lon</code>"
    override fun locAccuracy(a: String): String = "🎯 <b>Accuracy:</b> ±$a m"
    override fun locMethod(m: String): String = "📡 <b>Determination method:</b> $m"
    override fun locTimeAddr(t: String, a: String): String = "🕒 <b>Fix time:</b> $t$a"
    override fun locLinks(g: String, o: String): String = "🗺️ <a href=\"$g\">Google Maps</a> | <a href=\"$o\">OpenStreetMap</a>"
    override fun locAddrBlock(a: String): String = "\n🏠 <b>Address:</b> $a"
    override fun locBtnAgain(): String = "🔄 Request again"
    override fun locBtnInstant(): String = "⚡ Instant (cache)"
    override fun locBtnStartTrack(): String = "🛰️ Start tracking (/track)"
    override fun locBtnStopTrack(): String = "⏹ Stop tracking"
    override fun locBtnStatus(): String = "📊 Status"
    override fun locBtnSingle(): String = "📍 Single request (/location)"
    override fun locBtn30s(): String = "⏱ 30 sec"
    override fun locBtn1m(): String = "⏱ 1 min"
    override fun locBtn5m(): String = "⏱ 5 min"
    override fun locBtn15m(): String = "⏱ 15 min"
    override fun locBtn30m(): String = "⏱ 30 min"
    override fun locBtn60m(): String = "⏱ 60 min"
    override fun locGpsOn(): String = "✅ <b>ENABLED</b>"
    override fun locGpsOff(): String = "❌ <b>DISABLED</b>"
    override fun locGpsStatus(st: String): String = "🛰️ <b>GPS module:</b> $st\nToggle: <code>/gps on</code> | <code>/gps off</code>"
    override fun locGpsOnOff(on: Boolean): String = if (on) "enabled ✅" else "disabled ❌"
    override fun locGpsToggled(st: String): String = "🛰️ GPS module successfully $st"
    override fun locGpsToggleError(e: String): String = "❌ GPS toggle error: $e"
    override fun locGpsUsage(): String = "Format: <code>/gps on</code> or <code>/gps off</code>"
    override fun locShakeOpName(lvl: String, cd: String): String = "📳 Shake sensor ($lvl/10, ${cd}s)"
    override fun locShakeOpDesc(th: String, cd: String): String = "Threshold $th m/s², interval ${cd}s"
    override fun locShakeCdLabel(s: String, act: Boolean): String = if (act) "${s}s ✅" else "${s}s"
    override fun locShakeBtnOff(): String = "⏹ Turn off"
    override fun locShakeBtnOn(lvl: String, cd: String): String = "▶️ Turn on ($lvl/10, ${cd}s)"
    override fun locShakeBtnRefresh(): String = "🔄 Refresh"
    override fun locShakeActive(th: String, cd: String): String = "✅ <b>ACTIVE</b> (Threshold: $th m/s², Interval: ${cd}s)"
    override fun locShakeOff(): String = "❌ <b>DISABLED</b>"
    override fun locTrackStopped(points: String, m: String, s: String): String = "⏹ <b>Periodic tracking stopped.</b>\n\n• Points sent: <b>$points</b>\n• Tracker runtime: <b>${m}m ${s}s</b>"
    override fun locTrackStatus(st: String, iv: String, points: String, dur: String): String = "📊 <b>Periodic tracking status (/track):</b>\n\n• State: $st\n• Send interval: <b>$iv</b>\n• Points sent: <b>$points</b>\n• Runtime: <b>$dur</b>"
    override fun locTrackStarted(r: String): String = "🛰️ <b>Periodic tracking started!</b>\n\n⏱ Send interval: <b>every $r</b>\n📍 Coordinates will be periodically sent to this chat until stopped.\n\n<i>To stop, press the button below or send <code>/track stop</code>.</i>"
    override fun locTrackPanel(st: String): String = "🛰️ <b>Periodic tracking control (/track):</b>\n\n• Current status: $st\n• Choose the location send interval using buttons:"
    override fun locShakePanel(st: String, lvl: String, th: String, cd: String): String = "📳 <b>Security shake detector (Accelerometer)</b>\n\n• Status: $st\n• Current level: <b>$lvl / 10</b> (Threshold: $th m/s²)\n• Alarm send interval: <b>$cd sec</b> (anti-spam)\n\n<i>Runs continuously until explicitly stopped. On shake it sends an alarm no more often than the selected interval.</i>\n<i>Scale: 1 = coarse (30 m/s², strong hits/falls), 10 = ultrasensitive (1 m/s², slightest movement).</i>\n\n👇 <b>Sensitivity (1..10):</b>"
    override fun locResultBody(coords: String, acc: String, method: String, time: String, addr: String, gmaps: String, osm: String): String = "🌐 <b>Coordinates:</b> <code>$coords</code>\n🎯 <b>Accuracy:</b> ±$acc m\n📡 <b>Determination method:</b> $method\n🕒 <b>Fix time:</b> $time$addr\n🗺️ <a href=\"$gmaps\">Google Maps</a> | <a href=\"$osm\">OpenStreetMap</a>"
    override fun locSatsFragment(used: String, total: String): String = " | 🛰️ Satellites: $used in fix / $total visible"

    override fun bkpUnknownCmd(cmd: String): String = "❌ Unknown backup command: $cmd"
    override fun bkpFileCaption(): String = "💾 <b>Remote Bot configuration backup</b>\n\n<i>To restore, send this file to the bot or run:</i>\n<code>/restore_backup &lt;json&gt;</code>"
    override fun bkpJsonBlock(json: String): String = "💾 <b>Configuration backup:</b>\n\n<pre><code class=\"language-json\">\n$json\n</code></pre>\n\n<i>To restore, send the command:</i>\n<code>/restore_backup &lt;json&gt;</code>"
    override fun bkpRestoreHelp(): String = "📥 <b>Remote Bot configuration restore</b>\n\nTwo options available:\n1. <b>Send a file</b>: just send the <code>.json</code> backup file to this chat (optionally with the caption <code>/restore_backup</code>).\n2. <b>Paste text</b>: send the command with the JSON content:\n<code>/restore_backup &lt;json-text&gt;</code>"
    override fun bkpRestored(version: String): String = "✅ <b>Configuration restored successfully</b> from backup (v$version)!"
    override fun bkpUnknownError(): String = "Unknown error"
    override fun bkpDeserializeError(err: String): String = "❌ Backup deserialization error: $err"
    override fun pxStatusOff(): String = "🌐 <b>Current status:</b> Disabled (direct connection, no proxy)"
    override fun pxAuthWith(user: String): String = "with auth (<code>$user</code>)"
    override fun pxAuthNone(): String = "no auth"
    override fun pxWsPath(path: String): String = "\n• WSS path: <code>$path</code>"
    override fun pxStatusBlock(type: String, addr: String, auth: String, extra: String): String = "🌐 <b>Current status:</b>\n• Type: <b>$type</b>\n• Address: <code>$addr</code>\n• Auth: $auth$extra"
    override fun pxHelp(status: String): String = "$status\n\n━━━━━━━━━━━━━━━━━━━━━\n📖 <b>Management and command examples:</b>\n\n1️⃣ <b>SOCKS5 proxy:</b>\n<code>/proxy socks5 &lt;host&gt; &lt;port&gt; [login] [password]</code>\n<i>Example without password:</i>\n<code>/proxy socks5 192.168.1.100 1080</code>\n<i>Example with auth:</i>\n<code>/proxy socks5 1.2.3.4 10808 myuser mypass</code>\n\n2️⃣ <b>HTTP proxy:</b>\n<code>/proxy http &lt;host&gt; &lt;port&gt; [login] [password]</code>\n<i>Example:</i>\n<code>/proxy http 192.168.1.50 8080 admin secret</code>\n\n3️⃣ <b>WSS WebSocket tunnel:</b>\n<code>/proxy wss &lt;host&gt; &lt;port&gt; [path]</code>\n<i>Example:</i>\n<code>/proxy wss myproxy.example.com 443 /telegram-ws</code>\n\n4️⃣ <b>Disabling the proxy:</b>\n<code>/proxy off</code>"
    override fun pxDisabled(): String = "🌐 Proxy disabled. Direct connection is used."
    override fun pxFormat(sub: String, tail: String): String = "Format: /proxy $sub &lt;host&gt; &lt;port&gt; $tail"
    override fun pxBadPort(port: String): String = "Invalid port: $port"
    override fun pxSet(type: String, addr: String): String = "✅ Proxy switched to <b>$type</b> ($addr)"
    override fun pxWssSet(url: String): String = "✅ <b>WSS tunnel</b> configured ($url)"
    override fun pxUnknownParam(sub: String, available: String): String = "Unknown /proxy parameter: $sub. Available: $available"
    override fun shUnknownCmd(cmd: String): String = "❌ Unknown shell command: $cmd"
    override fun shHelp(): String = "💻 <b>Linux Shell Terminal</b>\n\nUsage: <code>/shell &lt;command&gt;</code>\nExamples:\n• <code>/shell uname -a</code>\n• <code>/shell df -h</code>\n• <code>/shell getprop ro.build.version.release</code>\n• <code>/shell ip addr show</code>\n• <code>/shell ls -la /sdcard</code>"
    override fun shNoOutput(code: Int): String = "(The command finished with no text output, exit code: $code)"
    override fun shTruncated(limit: Int): String = "\n\n⚠️ [Output exceeded the limit, showing the first $limit characters]"
    override fun shResultBlock(emoji: String, mode: String, cmd: String, code: Int, out: String): String = "$emoji <b>[$mode] $cmd</b>\n<i>Exit code: $code</i>\n\n<pre>$out</pre>"
    override fun shTimeout(): String = "⏱ Timeout: command execution took more than 30 seconds and was interrupted."
    override fun shExecError(msg: String): String = "❌ Execution error: $msg"
    override fun shDelayedFooter(status: String): String = "$status\n<i>⏳ Execution will happen in 10 seconds...</i>"
    override fun shRootRestart(): String = "❌ Superuser (Root) access is required to reboot. Grant Root in Magisk/KernelSU/APatch."
    override fun shRootShutdown(): String = "❌ Superuser (Root) access is required to power off. Grant Root in Magisk/KernelSU/APatch."
    override fun shRootRecovery(): String = "❌ Superuser (Root) access is required to boot into Recovery. Grant Root in Magisk/KernelSU/APatch."
    override fun shRootBootloader(): String = "❌ Superuser (Root) access is required to boot into Bootloader. Grant Root in Magisk/KernelSU/APatch."
    override fun shStatusReboot(): String = "🔄 <b>The device will be rebooted</b>"
    override fun shStatusShutdown(): String = "⏻ <b>The device will be powered off</b>"
    override fun shStatusRecovery(): String = "🛠 <b>The device will reboot into Recovery mode</b>"
    override fun shStatusFastboot(): String = "⚡ <b>The device will reboot into Bootloader (Fastboot) mode</b>"
    override fun faUnknownCmd(cmd: String): String = "❌ Unknown file/app management command: $cmd"
    override fun faGetFileUsage(): String = "Format: <code>/get_file &lt;file_or_folder_path&gt; [zip]</code>\nExample: <code>/get_file /sdcard/DCIM/Camera/photo.jpg</code>\nFolder example: <code>/get_file /sdcard/Documents zip</code>"
    override fun faArchiveCaption(name: String, size: String): String = "📦 Archive: <code>$name</code> ($size)"
    override fun faZipError(msg: String): String = "❌ Failed to create zip archive: $msg"
    override fun faFileCaption(name: String, size: String): String = "📄 File: <code>$name</code> ($size)"
    override fun faGetFileError(msg: String): String = "❌ Failed to get file: $msg"
    override fun faDownloadUsage(): String = "Format: <code>/download &lt;URL&gt; [destination_path]</code>\nExample: <code>/download https://example.com/file.apk</code>"
    override fun faDownloaded(path: String, size: String): String = "✅ <b>File downloaded successfully:</b>\n<code>$path</code> ($size)"
    override fun faDownloadError(msg: String): String = "❌ File download error: $msg"
    override fun faDirEmpty(path: String): String = "📁 Directory is empty: <code>$path</code>"
    override fun faDirContentHeader(path: String): String = "📁 <b>Directory contents:</b> <code>$path</code>\n\n"
    override fun faMoreItems(count: Int): String = "\n<i>... and $count more items</i>"
    override fun faDirReadError(msg: String): String = "❌ Directory read error: $msg"
    override fun faBtnUp(): String = "⬆️ Up"
    override fun faBtnRefresh(): String = "🔄 Refresh"
    override fun faBtnZip(): String = "📦 Download ZIP"
    override fun faExplorerHeader(path: String, dirs: Int, files: Int, total: Int): String = "📂 <b>Explorer:</b> <code>$path</code>\n<i>Folders: $dirs, Files: $files (Total: $total)</i>\n\n👉 <i>Tap a folder to open it. Tap a file to send it to the chat.</i>"
    override fun faNoApps(): String = "📱 No applications found."
    override fun faAppsHeader(count: Int): String = "📱 <b>Installed applications ($count):</b>\n\n"
    override fun faMoreApps(count: Int): String = "<i>... and $count more apps. Use the filter: /apps &lt;search&gt;</i>\n"
    override fun faLaunchHint(): String = "\n<i>Launch: /app_launch &lt;package&gt;</i>"
    override fun faLaunchUsage(): String = "Format: <code>/app_launch &lt;package_name&gt;</code>\nExample: <code>/app_launch com.android.settings</code>"
    override fun faAppLaunched(pkg: String): String = "🚀 <b>Application launched:</b> <code>$pkg</code>"
    override fun faLaunchError(msg: String): String = "❌ Launch error: $msg"
    override fun faInstallHelp(): String = "📦 <b>Installing APK packages</b>\n\nTwo options available:\n1. <b>Send a file to the chat</b>: just attach and send the <code>.apk</code> file to the bot (optionally with the caption <code>/install</code>). The bot will download and install it automatically.\n2. <b>Specify a local path</b>: \n<code>/install &lt;path_to_apk&gt;</code>\n<i>Example:</i> <code>/install /sdcard/Download/app.apk</code>"
    override fun faApkInstalled(path: String): String = "✅ <b>APK package installed successfully:</b>\n<code>$path</code>"
    override fun faInstallError(msg: String): String = "❌ APK installation error: $msg"
    override fun faNoProcesses(): String = "⚙️ No active processes."
    override fun faTaskManagerHeader(): String = "⚙️ <b>Process manager (TaskManager):</b>\n\n"
    override fun faKillHint(): String = "\n<i>Kill: /kill &lt;PID&gt; or /kill &lt;package&gt;</i>"
    override fun faProcessListError(msg: String): String = "❌ Failed to get the process list: $msg"
    override fun faKillUsage(): String = "Format: <code>/kill &lt;PID&gt;</code> or <code>/kill &lt;package_name&gt;</code>\nExample: <code>/kill 1234</code> or <code>/kill com.example.app</code>"
    override fun faProcessKilled(target: String): String = "🛑 Process / application <code>$target</code> was force-stopped."
    override fun faKillError(msg: String): String = "❌ Kill error: $msg"
    override fun faUnsentEmpty(): String = "📤 There are no unsent files in the queue."
    override fun faUnsentHeader(count: Int): String = "📤 <b>Unsent files queue ($count):</b>\n\n"
    override fun faUnsentLine(path: String, type: String, attempts: Int): String = "• <code>$path</code> (Type: $type, Attempts: $attempts)"
    override fun tnUnknownCmd(cmd: String): String = "❌ Unknown telephony command: $cmd"
    override fun tnSmsUsage(): String = "Format: <code>/sms [sim] &lt;number&gt; &lt;text&gt;</code>\nExample: <code>/sms +79991234567 Hello!</code>\nSIM2 example: <code>/sms 2 +79991234567 Hello!</code>"
    override fun tnSmsNeedArgs(): String = "Please provide a phone number and the message text.\nExample: <code>/sms +79991234567 Hello!</code>"
    override fun tnSmsSent(phone: String, sim: String, text: String): String = "📨 <b>SMS sent successfully</b> to <code>$phone</code>$sim:\n«$text»"
    override fun tnSmsError(msg: String): String = "❌ SMS sending error: $msg"
    override fun tnSmsInboxEmpty(): String = "📭 No incoming SMS found."
    override fun tnSmsInboxHeader(limit: Int): String = "📬 <b>Recent SMS ($limit):</b>\n\n"
    override fun tnSmsReadError(msg: String): String = "❌ Failed to read SMS: $msg"
    override fun tnCallUsage(): String = "Format: <code>/call [sim] &lt;number&gt;</code>\nExample: <code>/call +79991234567</code>"
    override fun tnCallNeedNumber(): String = "Please provide a phone number to call."
    override fun tnCallInitiated(phone: String, sim: String): String = "📞 <b>Outgoing call initiated</b> to <code>$phone</code>$sim..."
    override fun tnCallError(msg: String): String = "❌ Call error: $msg"
    override fun tnCallsEmpty(): String = "📞 Call log is empty."
    override fun tnCallsHeader(limit: Int): String = "📞 <b>Call log (last $limit):</b>\n\n"
    override fun tnCallDuration(min: Long, sec: Long): String = " [$min min $sec s]"
    override fun tnCallMeta(time: String, type: String): String = "   <i>$time | Type: $type</i>"
    override fun tnCallsReadError(msg: String): String = "❌ Failed to get the call log: $msg"
    override fun tnUssdUsage(): String = "Format: <code>/ussd [sim] &lt;code&gt;</code>\nExample: <code>/ussd *100#</code>"
    override fun tnUssdNeedCode(): String = "Please provide a USSD code, e.g. <code>*100#</code>"
    override fun tnUssdResponse(sim: String, code: String, response: String): String = "📟 <b>USSD Response$sim for <code>$code</code>:</b>\n\n<code>$response</code>"
    override fun tnUssdError(msg: String): String = "❌ USSD request error: $msg"
    override fun tnNotifEmpty(): String = "🔔 No intercepted notifications.\n<i>Make sure the bot has access in Settings -> Notification access.</i>"
    override fun tnNotifHeader(limit: Int): String = "🔔 <b>Recent notifications ($limit):</b>\n\n"
    override fun tnNotifReplyBadge(id: String): String = " [💬 <i>Reply: /answer $id text</i>]"
    override fun tnNotifyUsage(): String = "Format: <code>/notify &lt;title&gt; &lt;text&gt;</code>\nExample: <code>/notify Attention Important message</code>"
    override fun tnNotifyPosted(title: String, text: String): String = "🔔 <b>Notification posted to the phone status bar:</b>\nTitle: <b>$title</b>\nText: $text"
    override fun tnNotifyError(msg: String): String = "❌ Notification sending error: $msg"
    override fun tnFakeNotifyUsage(): String = "Format: <code>/fake_notify &lt;package&gt; &lt;title&gt; &lt;text&gt;</code>\nExample: <code>/fake_notify ru.sberbankmobile Sberbank Transfer +5000r from Diana</code>\nExample 2: <code>/fake_notify com.whatsapp Diana Hi, how are you?</code>"
    override fun tnAppSber(): String = "Sberbank"
    override fun tnAppTbank(): String = "T-Bank"
    override fun tnFakeNotifySimulated(app: String, title: String, text: String): String = "🧪 <b>Simulated incoming notification from [$app]:</b>\nFrom: <b>$title</b>\nText: $text"
    override fun tnFakeNotifyError(msg: String): String = "❌ Notification simulation error: $msg"
    override fun tnAnswerUsage(): String = "Format: <code>/answer &lt;ID&gt; &lt;reply text&gt;</code>\nExample: <code>/answer 1 Hi, will be there soon!</code>"
    override fun tnAnswerSent(id: String, text: String): String = "💬 <b>Reply sent successfully</b> to notification #$id:\n«$text»"
    override fun tnAnswerError(msg: String): String = "❌ Failed to send the reply: $msg"
    override fun tnActUsage(): String = "Format: <code>/notify_act &lt;ID&gt; [action_number]</code>\nExample: <code>/notify_act 1 0</code>"
    override fun tnActDone(index: Int, id: String): String = "⚡ <b>Action (index $index) executed successfully</b> for notification #$id!"
    override fun tnActError(msg: String): String = "❌ Action execution error: $msg"
    override fun tnClearUsage(): String = "Format: <code>/notify_clear &lt;ID/all&gt;</code>"
    override fun tnCleared(id: String): String = "🗑️ Notification #$id removed."
    override fun tnClearError(msg: String): String = "❌ Notification removal error: $msg"
    override fun tnRulesEmpty(): String = "📋 <b>Notification filter rules are empty.</b>\nAdd: <code>/notify_rule add &lt;pkg&gt; [pattern]</code>"
    override fun tnRulesHeader(): String = "📋 <b>Notification filter rules:</b>\n\n"
    override fun tnRuleAllow(): String = "✅ Allow"
    override fun tnRuleExclude(): String = "⛔ Exclude"
    override fun tnRuleAdded(id: String): String = "✅ Filter rule #$id added"
    override fun tnRuleDeleted(id: String): String = "🗑️ Rule #$id removed."
    override fun tnRulesUsage(): String = "Format: <code>/notify_rule list</code> or <code>/notify_rule add exclude &lt;pkg&gt;</code> or <code>/notify_rule del &lt;id&gt;</code>"
    override fun mdUnknownCmd(cmd: String): String = "❌ Unknown media command: /$cmd"
    override fun mdLensFront(): String = "Front"
    override fun mdLensBack(): String = "Main"
    override fun mdLensFrontSelfie(): String = "Front (selfie)"
    override fun mdLensBackRear(): String = "Main (rear)"
    override fun mdLensFrontLower(): String = "front"
    override fun mdLensBackLower(): String = "main"
    override fun mdFlashOn(): String = "Flash: ON"
    override fun mdFlashOff(): String = "Flash: OFF"
    override fun mdPhotoCaption(lens: String, flash: String, rotation: String): String = "📷 <b>Photo from camera</b>\nCamera: $lens\n$flash\nRotation: $rotation°"
    override fun mdPhotoError(msg: String): String = "Photo capture error: $msg"
    override fun mdRecStopped(): String = "⏹ Camera video recording stopped. Processing and sending..."
    override fun mdNoActiveRec(): String = "⚠️ There is no active video recording to stop."
    override fun mdRecActive(name: String): String = "🔴 <b>Recording in progress:</b> $name"
    override fun mdRecIdle(): String = "⏹ <b>Idle</b> (no active recording)"
    override fun mdRecPanel(status: String, lens: String): String = "📹 <b>Hidden camera video recording (/videorec):</b>\n\n• Status: $status\n• Selected camera: <b>$lens</b>\n• Choose a duration with the buttons below (up to 1 hour) or start a long recording that can be stopped at any moment:"
    override fun mdRecOpName(lens: String, dur: String): String = "📹 Camera video recording ($lens, $dur)"
    override fun mdRecOpDesc(lens: String, dur: String): String = "Camera $lens, duration up to $dur"
    override fun mdRecCaption(lens: String, sec: Int): String = "📹 <b>Camera video recording</b> ($lens, $sec s)"
    override fun mdRecError(msg: String): String = "Video recording error: $msg"
    override fun mdDurSec(sec: Int): String = "$sec s"
    override fun mdDurMin(min: Int): String = "$min min"
    override fun mdDurMinSec(min: Int, sec: Int): String = "$min min $sec s"
    override fun mdBtnToggleToBack(): String = "🔄 Switch to Main"
    override fun mdBtnToggleToFront(): String = "🔄 Switch to Front"
    override fun mdBtnDur(sec: Int): String = "⏱ $sec s"
    override fun mdBtnDurMin(sec: Int, min: Int): String = "⏱ $sec s ($min min)"
    override fun mdBtnDurHour(sec: Int): String = "⏱ $sec s (1 hour)"
    override fun mdBtnRecUntilStop(): String = "🔴 Record until stopped"
    override fun mdBtnStopRec(): String = "⏹ Stop video recording"
    override fun mdBtnRefreshPanel(): String = "🔄 Refresh panel"
    override fun mdBtnStop(): String = "⏹ Stop"
    override fun mdBtnDisable(): String = "⏹ Disable"
    override fun mdBtnEnable(level: Int, cooldown: Int): String = "▶️ Enable ($level/10, $cooldown s)"
    override fun mdBtnRefresh(): String = "🔄 Refresh"
    override fun mdSec(sec: Int): String = "$sec s"
    override fun mdSecActive(sec: Int): String = "$sec s ✅"
    override fun mdMotionActive(percent: Int, cooldown: Int): String = "✅ <b>ACTIVE</b> (Threshold: $percent%, Interval: $cooldown s)"
    override fun mdMotionInactive(): String = "❌ <b>DISABLED</b>"
    override fun mdMotionPanel(status: String, level: Int, percent: Int, cooldown: Int): String = "👁 <b>Motion detector (Camera)</b>\n\n• Status: $status\n• Sensitivity: <b>$level / 10</b> ($percent%)\n• Photo sending interval: <b>$cooldown s</b> (anti-spam)\n\n<i>Runs continuously until explicitly stopped. When motion is detected, the bot sends an alert with a photo no more often than the selected interval.</i>\n<i>Sensitivity scale: 1 (10%) — large movements, 10 (100%) — the slightest motion.</i>\n\n👇 <b>Sensitivity (1..10):</b>"
    override fun mdMotionOpName(level: Int, cooldown: Int): String = "👁 Camera motion detector ($level/10, $cooldown s)"
    override fun mdMotionOpDesc(percent: Int, cooldown: Int): String = "Threshold $percent%, interval $cooldown s"
    override fun mdGalleryEmpty(): String = "🖼 <b>Gallery</b>: no new photos found in storage."
    override fun mdGalleryCaption(count: Int): String = "🖼 <b>Last $count photos from the gallery</b>"
    override fun siUnknownCmd(cmd: String): String = "❌ Unknown system information command: $cmd"
    override fun siUptimeDays(d: Long, h: Long, m: Long): String = "${d}d ${h}h ${m}m"
    override fun siUptimeHours(h: Long, m: Long): String = "${h}h ${m}m"
    override fun siNetCellular(): String = "Mobile network (LTE/5G)"
    override fun siNetOff(): String = "Disabled"
    override fun siRootGranted(): String = "🟢 Granted (UID 0)"
    override fun siRootDenied(): String = "🔴 None / Denied"
    override fun siSummary(model: String, os: String, build: String, patch: String, root: String, uptime: String, cores: Int, abis: String, ramUsed: Long, ramTotal: Long, ramPct: Int, romAvail: String, romTotal: String, screen: String, sensors: Int, netType: String, ip: String): String = "📱 <b>System Summary (System Info)</b>\n\n<b>Device:</b>\n• Model: <code>$model</code>\n• OS: <code>$os</code>\n• Build: <code>$build</code>\n• Security patch: <code>$patch</code>\n• Root access: <b>$root</b>\n• Uptime: <code>$uptime</code>\n\n<b>Hardware:</b>\n• CPU: <code>$cores cores ($abis)</code>\n• RAM: <code>$ramUsed / $ramTotal MB ($ramPct% used)</code>\n• ROM: <code>$romAvail GB free of $romTotal GB</code>\n• Screen: <code>$screen</code>\n• Sensors: <code>$sensors available</code>\n\n<b>Connectivity and Network:</b>\n• Connection type: <code>$netType</code>\n• Local IP: <code>$ip</code>"
    override fun siBtnTogglesPanel(): String = "🎛 Toggles panel"
    override fun siBtnBattery(): String = "🔋 Battery"
    override fun siBtnNetworkInterfaces(): String = "🌐 Network Interfaces"
    override fun siSysInfoError(msg: String): String = "System information retrieval error: $msg"
    override fun siBatteryReadError(): String = "Failed to read the battery state."
    override fun siBatCharging(): String = "⚡ Charging"
    override fun siBatDischarging(): String = "🔋 Discharging"
    override fun siBatFull(): String = "🟢 Fully charged (100%)"
    override fun siBatNotCharging(): String = "⚪ Not charging"
    override fun siBatUnknown(): String = "Unknown"
    override fun siPlugAc(): String = "AC (220V)"
    override fun siPlugUsb(): String = "USB port"
    override fun siPlugWireless(): String = "Wireless charging"
    override fun siPlugBattery(): String = "Battery (unplugged)"
    override fun siHealthGood(): String = "✅ Good"
    override fun siHealthOverheat(): String = "🔥 Overheat"
    override fun siHealthDead(): String = "💀 Dead"
    override fun siHealthOverVoltage(): String = "⚠️ Over voltage"
    override fun siHealthCold(): String = "❄️ Cold"
    override fun siHealthNormal(): String = "Normal"
    override fun siVoltage(volts: String, mv: Int): String = "$volts V ($mv mV)"
    override fun siBatteryBlock(icon: String, pct: Int, status: String, plug: String, temp: String, voltage: String, health: String, tech: String): String = "$icon <b>Battery Status (Battery)</b>\n\n• Charge level: <b>$pct%</b>\n• Status: <b>$status</b>\n• Power source: <code>$plug</code>\n• Temperature: <code>$temp</code>\n• Voltage: <code>$voltage</code>\n• Battery health: <code>$health</code>\n• Technology: <code>$tech</code>"
    override fun siPowerSaveOn(): String = "🔋 Battery Saver [ON]"
    override fun siPowerSaveOff(): String = "🔋 Battery Saver [OFF]"
    override fun siBtnRefresh(): String = "🔄 Refresh"
    override fun siBatParamsError(msg: String): String = "Failed to read battery parameters: $msg"
    override fun siNoInterfaces(): String = "🌐 <b>No network interfaces found.</b>"
    override fun siIfaceCellular(): String = "Mobile network (LTE/5G) 📱"
    override fun siIfaceAbsent(): String = "Absent ⚪"
    override fun siNetTitle(): String = "🌐 <b>Device network interfaces</b>\n\n"
    override fun siNetPrimary(transport: String): String = "• <b>Primary connection:</b> $transport\n"
    override fun siNoAddresses(): String = "  • IP: <i>no addresses</i>\n"
    override fun siBtnMobileData(): String = "📱 Mobile data"
    override fun siNetError(msg: String): String = "Network interfaces retrieval error: $msg"
    override fun siUptimeText(app: String, os: String): String = "⏱️ <b>Continuous Uptime</b>\n\n🤖 <b>Bot Service:</b> <code>$app</code>\n📱 <b>Android System:</b> <code>$os</code>"
    override fun siNA(): String = "N/A"
    override fun siBtnStatus(): String = "📊 Status"
    override fun siBtnToggles(): String = "🎛️ Toggles"
    override fun siUnitD(): String = "d "
    override fun siUnitH(): String = "h "
    override fun siUnitM(): String = "m "
    override fun siUnitS(): String = "s"
    override fun scuBtnDisableRoot(): String = "🔒 Disable (Root)"
    override fun scuBtnEnableRoot(): String = "🔒 Enable (Root)"
    override fun scuBtnDisableBolt(): String = "🔴 Disable ⚡"
    override fun scuBtnEnableBolt(): String = "🟢 Enable ⚡"
    override fun scuBtnDisable(): String = "🔴 Disable"
    override fun scuBtnEnable(): String = "🟢 Enable"
    override fun scuBtnAllToggles(): String = "🎛 All Toggles"
    override fun scUnknownCmd(cmd: String): String = "❌ Unknown system control command: $cmd"
    override fun srUnknownCmd(cmd: String): String = "❌ Unknown screen command: /$cmd"
    override fun srScreenshotCaption(method: String): String = "🖥 <b>Screenshot</b>\nMethod: $method"
    override fun srScreenshotError(msg: String): String = "Screen capture failed: $msg"
    override fun srRecOpName(sec: Int): String = "📹 Screen recording ($sec s)"
    override fun srRecOpDesc(): String = "Background screen recording to MP4"
    override fun srRecCaption(sec: Int): String = "📹 <b>Screen recording</b> ($sec s)"
    override fun srRecError(msg: String): String = "Screen recording failed: $msg"
    override fun autUnknownCmd(cmd: String): String = "❌ Unknown automation command: $cmd"
    override fun autTaskerInstalled(): String = "✅ Installed"
    override fun autTaskerMissing(): String = "⚠️ Not found (please install Tasker)"
    override fun autTaskerHelp(state: String): String = "🤖 <b>Tasker integration:</b> $state\n\nUsage: <code>/tasker &lt;TaskName&gt; [par1] [par2]</code>\nExample: <code>/tasker NightMode 1 on</code>"
    override fun autParams(params: String): String = " (Parameters: $params)"
    override fun autTaskerSent(task: String, extra: String): String = "✅ <b>Tasker task sent for execution:</b> <code>$task</code>$extra"
    override fun autTaskerError(msg: String): String = "❌ Failed to send the task to Tasker: $msg"
    override fun autScriptUsage(): String = "Format: <code>/script &lt;script_code&gt;</code>\nExample: <code>/script date +%Y-%m-%d</code>\nExample: <code>/script echo 'Hello world'</code>"
    override fun autScriptOk(ms: Long, out: String): String = "⚡ <b>Script execution result ($ms ms):</b>\n\n<code>$out</code>"
    override fun autScriptErr(ms: Long, err: String, out: String): String = "❌ <b>Execution error ($ms ms):</b>\n$err\n\nOutput:\n<code>$out</code>"
    override fun autNoTimers(): String = "⏱ <b>No active timers.</b>\nCreate: <code>/timers add 60s /photo</code> or <code>/timers add 10m /location</code>"
    override fun autTimersHeader(count: Int): String = "⏱ <b>Scheduled timers ($count):</b>\n\n"
    override fun autIntervalRepeat(sec: Long): String = " [repeat every $sec s]"
    override fun autIntervalOnce(): String = " [once]"
    override fun autTimerLine(id: String, cmd: String, time: String, interval: String): String = "• <b>ID: $id</b> | <code>$cmd</code>\n   <i>Triggers at $time$interval</i>\n\n"
    override fun autTimerDelHint(): String = "<i>Delete a timer: /timers del &lt;ID&gt;</i>"
    override fun autBadDelay(delay: String): String = "Invalid delay time format: <code>$delay</code> (examples: 30s, 5m, 2h)"
    override fun autTimerCreated(id: String, cmd: String, delay: String): String = "✅ <b>Timer #$id created!</b>\nCommand <code>$cmd</code> will run in $delay."
    override fun autTimerDeleted(id: String): String = "🗑️ Timer #$id removed."
    override fun autTimersUsage(): String = "Format: <code>/timers list</code> or <code>/timers add &lt;time&gt; &lt;command&gt;</code> or <code>/timers del &lt;ID&gt;</code>"
    override fun evStatusOn(): String = "🟢 On"
    override fun evStatusOff(): String = "🔴 Off"
    override fun evRoleCommandsOnly(): String = "🕹 <b>Control only</b> (broadcast disabled)"
    override fun evRoleRecipient(): String = "📬 <b>Event recipient</b>"
    override fun evUserSelf(id: Long): String = "your account (ID: <code>$id</code>)"
    override fun evUserOther(id: Long): String = "user (ID: <code>$id</code>)"
    override fun evText(user: String, role: String, notif: String, sms: String, calls: String, system: String): String = "⚙️ <b>Event broadcast for $user</b>\n\nRole: $role\n\n• 🔔 Push notifications: <b>$notif</b>\n• 💬 Incoming SMS: <b>$sms</b>\n• 📞 Calls / Missed: <b>$calls</b>\n• ⚙️ System (battery, power, sensors): <b>$system</b>\n\n<i>Tap the buttons below to toggle categories:</i>"
    override fun evBtnNotifOn(): String = "🔔 Notifications: [ON]"
    override fun evBtnNotifOff(): String = "🔕 Notifications: [OFF]"
    override fun evBtnSmsOn(): String = "💬 SMS: [ON]"
    override fun evBtnSmsOff(): String = "🚫 SMS: [OFF]"
    override fun evBtnCallsOn(): String = "📞 Calls: [ON]"
    override fun evBtnCallsOff(): String = "📵 Calls: [OFF]"
    override fun evBtnSystemOn(): String = "⚙️ System: [ON]"
    override fun evBtnSystemOff(): String = "💤 System: [OFF]"
    override fun evBtnAllOff(): String = "🔕 Commands only (turn everything off)"
    override fun evBtnAllOn(): String = "🔔 Turn everything on"
    override fun evBtnRefresh(): String = "🔄 Refresh"
    override fun bsRootAvail(): String = "✅ Available (UID 0)"
    override fun bsRootMissing(): String = "❌ Missing / Not granted"
    override fun bsRootActive0(): String = "✅ ACTIVE (UID 0)"
    override fun bsRootRestricted(): String = "⚠️ RESTRICTED (no Root)"
    override fun bsApiOk(ms: Long): String = "🟢 Available (${ms}ms)"
    override fun bsApiErr(err: String): String = "🔴 Error ($err)"
    override fun bsTimeout(): String = "🔴 Timeout"
    override fun bsTimeoutWord(): String = "Timeout"
    override fun bsLatency(ms: Long): String = "🟢 ${ms}ms"
    override fun bsUnreachable(): String = "🔴 Unreachable"
    override fun bsEndpointItem(root: String): String = "  • <code>$root</code>"
    override fun bsIndented(value: String): String = "  <code>$value</code>"
    override fun bsPushTitle(): String = "🚀 <b>Push Services Network Reachability:</b>"
    override fun bsGwLine(status: String): String = "• <b>Webhook Push Gateway:</b> $status"
    override fun bsFcmLine(status: String): String = "• <b>Google FCM:</b> $status (<code>fcm.googleapis.com</code>)"
    override fun bsHmsLine(status: String): String = "• <b>Huawei HMS:</b> $status (<code>push-api.cloud.huawei.com</code>)"
    override fun bsPing(ms: Long): String = "🏓 Pong! Processing latency: ${ms}ms"
    override fun bsEchoEmpty(): String = "Empty input."
    override fun bsEchoPrefix(): String = "📢 Echo: "
    override fun bsUnknownCmd(cmd: String): String = "❌ Unknown service command: $cmd"
    override fun bsPermProviderMissing(): String = "Permission status provider is not initialized."
    override fun bsPermLine(icon: String, name: String, state: String): String = "$icon <b>$name:</b> $state"
    override fun bsRefreshStatus(): String = "🔄 Refresh Status"
    override fun bmUnknownCmd(cmd: String): String = "❌ Unknown management command: $cmd"
    override fun bmNoOps(): String = "⚙️ No active background operations."
    override fun bmOpsHeader(count: Int): String = "⚙️ <b>Active background operations ($count):</b>\n\n"
    override fun bmOpDuration(sec: Long): String = "   • Duration: ${sec}s"
    override fun bmOpInfo(desc: String): String = "   • Info: $desc"
    override fun bmStopHint(): String = "\nUse /stop_all for an emergency stop"
    override fun bmStopped(count: Int): String = "🛑 Background operations aborted: <b>$count</b>"
    override fun bmLogEmpty(note: String): String = "📜 Log is empty$note."
    override fun bmLogFilterNote(level: String): String = " with level >= $level"
    override fun bmLogLevelHeader(level: String): String = " (level >= $level)"
    override fun bmLogHeader(count: Int, header: String): String = "📜 <b>Last $count log entries$header:</b>\n\n"
    override fun bmLogUsage(): String = "\n<i>Usage: /log [N] [TRACE|DEBUG|INFO|WARN|ERROR]</i>"
    override fun bmUnsentEmpty(): String = "📁 <b>Unsent files queue:</b>\nQueue is empty. All files have been delivered."
    override fun bmActive(): String = "✅ Active"
    override fun bmDisabled(): String = "❌ Disabled"
    override fun bmInlineOn(): String = "On ✅"
    override fun bmInlineOff(): String = "Off ❌"
    override fun bmStatusPanel(tg: String, mode: String, sms: String, tgSms: String, tgCalls: String, tgPush: String, tgCharge: String, tgLow: String, smsSms: String, smsCalls: String, smsPush: String, smsCharge: String, smsLow: String, smsTranslit: String, targets: Int): String = "🤖 <b>Remote Bot channels and forwarding status:</b>\n\n• <b>Telegram Bot:</b> $tg (mode: <code>$mode</code>)\n  - SMS forwarding: $tgSms\n  - Call forwarding: $tgCalls\n  - Push forwarding: $tgPush\n  - Charging/power status: $tgCharge\n  - Low battery (≤20% every 3%): $tgLow\n\n• <b>SMS Bot:</b> $sms\n  - SMS forwarding: $smsSms\n  - Call forwarding: $smsCalls\n  - Push forwarding: $smsPush\n  - Charging/power status: $smsCharge\n  - Low battery (≤20% every 3%): $smsLow\n  - Transliteration: $smsTranslit\n  - Forwarding numbers: $targets\n\n<i>Management:</i>\n<code>/tg_bot [on|off]</code> — enable/disable Telegram\n<code>/sms_bot [on|off]</code> — enable/disable the SMS channel\n<code>/tg_forward [sms|calls|push|battery_charge|battery_low|battery] [on|off]</code> — forwarding to Telegram\n<code>/sms_forward [on|off|missed|push|battery_charge|battery_low|battery|translit]</code> — forwarding over SMS"
    override fun bmTgForwardPanel(sms: String, calls: String, push: String, charge: String, low: String): String = "✈️ <b>Event forwarding to Telegram:</b>\n• Incoming SMS: $sms\n• Calls: $calls\n• Push notifications: $push\n• Charging and power status: $charge\n• Low battery (≤20% every 3%): $low\n\n<i>Management:</i>\n<code>/tg_forward sms on|off</code> — SMS forwarding to Telegram\n<code>/tg_forward calls on|off</code> — call forwarding to Telegram\n<code>/tg_forward push on|off</code> — Push forwarding to Telegram\n<code>/tg_forward battery_charge on|off</code> — charger connect/disconnect alerts\n<code>/tg_forward battery_low on|off</code> — low battery alerts (≤20% every 3%)\n<code>/tg_forward battery on|off</code> — enable/disable all battery alerts"
    override fun bmFwdTgSms(state: String): String = "Incoming SMS forwarding to Telegram: $state"
    override fun bmFwdTgCalls(state: String): String = "Call forwarding to Telegram: $state"
    override fun bmFwdTgPush(state: String): String = "Push notification forwarding to Telegram: $state"
    override fun bmFwdTgCharge(state: String): String = "Charging and power status alerts in Telegram: $state"
    override fun bmFwdTgLow(state: String): String = "Low battery alerts (≤20% every 3%) in Telegram: $state"
    override fun bmFwdTgBattery(state: String): String = "All battery and charging alerts in Telegram: $state"
    override fun bmTgForwardUnknownType(): String = "Unknown type. Available: sms, calls, push, battery_charge, battery_low, battery. Example: <code>/tg_forward battery_low on</code>"
    override fun bmTgBotState(state: String): String = "🤖 <b>Telegram bot</b> is currently $state.\nUse: <code>/tg_bot on</code> or <code>/tg_bot off</code>"
    override fun bmTgBotUsage(): String = "Format: <code>/tg_bot on</code> or <code>/tg_bot off</code>"
    override fun bmTgBotResult(state: String): String = "🤖 <b>Telegram bot</b> $state"
    override fun bmSmsBotState(state: String): String = "💬 <b>SMS bot</b> is currently $state.\nUse: <code>/sms_bot on</code> or <code>/sms_bot off</code>"
    override fun bmSmsBotUsage(): String = "Format: <code>/sms_bot on</code> or <code>/sms_bot off</code>"
    override fun bmSmsBotResult(state: String): String = "💬 <b>SMS bot</b> $state"
    override fun bmBotToggleUsage(): String = "Format: <code>/bot_toggle &lt;tg|sms&gt; [on|off]</code>"
    override fun bmUnknownChannel(channel: String): String = "Unknown channel: $channel. Available: tg, sms"
    override fun bmStateOn(): String = "enabled ✅"
    override fun bmStateOff(): String = "disabled ❌"
    override fun bmEnabledFem(): String = "Enabled ✅"
    override fun bmDisabledFem(): String = "Disabled ⏹"
    override fun bmEnabledPlur(): String = "Enabled ✅"
    override fun bmDisabledPlur(): String = "Disabled ⏹"
    override fun bmTgOnRes(): String = "enabled ✅ (command reception resumed)"
    override fun bmTgOffRes(): String = "disabled ❌ (command polling suspended)"
    override fun bmSmsOnRes(): String = "enabled ✅ (SMS command reception and forwarding active)"
    override fun bmSmsOffRes(): String = "disabled ❌ (cellular sending disabled)"
    override fun rsFileNotFound(path: String): String = "⚠️ File not found: $path"
    override fun rsPhotoNotFound(path: String): String = "⚠️ Photo not found: $path"
    override fun rsVideoNotFound(path: String): String = "⚠️ Video not found: $path"
    override fun rsVoiceNotFound(path: String): String = "⚠️ Voice message not found: $path"
    override fun rsAudioNotFound(path: String): String = "⚠️ Audio file not found: $path"
    override fun rsMediaGroupNotFound(): String = "⚠️ Media group files not found"
    override fun rsSendError(msg: String): String = "⚠️ Failed to send the file to the chat: $msg"
    override fun engCmdError(msg: String): String = "⚠️ Command processing error: $msg"
    override fun engCbError(msg: String): String = "⚠️ <b>Processing error:</b> $msg"
    override fun engApkTooBigCloud(sizeMb: Long, apk: String): String = "⚠️ <b>The file exceeds the Telegram Cloud API limit ($sizeMb MB > 20 MB)</b>\n\nThe Telegram Bot API cloud service forbids bots to download files larger than 20 MB via getFile.\n\n💡 <b>How to install this APK:</b>\n1. Upload the file to the phone manually or via ADB:\n   <code>adb push \"$apk\" /sdcard/Download/</code>\n2. Send the install command to the bot:\n   <code>/install /sdcard/Download/$apk</code>\n\n<i>Alternatively, connect a local Telegram Bot API Server (files up to 2000 MB supported).</i>"
    override fun engApkDownloading(apk: String): String = "⏳ Downloading the APK package <code>$apk</code>..."
    override fun engApkTooBigSimple(apk: String): String = "❌ <b>The file exceeds the Telegram Cloud API limit (20 MB).</b>\nUpload the APK locally and run: <code>/install /sdcard/Download/$apk</code>"
    override fun engApkError(msg: String): String = "❌ APK download or installation error: $msg"
    override fun engBackupError(msg: String): String = "❌ Failed to upload the backup file: $msg"
    override fun tfUnauthorizedPin(): String = "Error: invalid SMS PIN or unauthorized number"
    override fun tfUnauthorizedNumber(): String = "Error: unauthorized sender number"
    override fun tfErrorReply(msg: String): String = "Error: $msg"
    override fun tfIncomingSmsHeader(from: String, sim: String, time: String): String = "📨 <b>Incoming SMS from $from</b>$sim (<i>$time</i>):\n"
    override fun tfReplyHint(): String = "<i>💬 Tap «Reply» below or use Reply on the message</i>"
    override fun tfBtnReplySms(): String = "💬 Reply via SMS"
    override fun tfSmsForwardText(sender: String, body: String): String = "SMS from $sender: $body"
    override fun tfUnknownNumber(): String = "Unknown number"
    override fun tfCallIncoming(): String = "📞 <b>Incoming Call</b>"
    override fun tfCallMissed(): String = "🔴 <b>Missed Call</b>"
    override fun tfFrom(): String = "from"
    override fun tfMissedCallText(caller: String): String = "Missed call from: $caller"
    override fun irEnterSmsText(): String = "Enter the SMS text in the input field"
    override fun irBtnCancel(): String = "❌ Cancel"
    override fun irSmsReplyPrompt(phone: String, sim: String): String = "✍️ <b>Enter the message text for</b> <code>$phone</code>$sim:\n<i>(Just type the text into the chat or tap «Cancel»)</i>"
    override fun irCancelled(): String = "Cancelled"
    override fun irSmsCancelled(): String = "❌ SMS sending cancelled."
    override fun irEnterReplyText(): String = "Enter the reply text"
    override fun irNotifReplyPrompt(id: String): String = "💬 <b>Reply to notification #$id:</b>\n<i>(Type the reply text into the chat or tap «Cancel»)</i>"
    override fun ir2faDisabled(): String = "❌ Experimental 2FA interception is disabled in the bot settings"
    override fun irActionDone(): String = "✅ Action executed!"
    override fun irActionError(msg: String): String = "❌ Error: $msg"
    override fun irActionFailed(): String = "Failed to execute"
    override fun irActionStatusOk(time: String): String = "\n\n<i>✅ Action executed at $time</i>"
    override fun irActionStatusErr(msg: String): String = "\n\n<i>❌ Action error: $msg</i>"
    override fun irTimeout(): String = "Timeout"
    override fun irSettingsUpdated(): String = "Settings updated"
    override fun irSmsSent(phone: String, sim: String, text: String): String = "✅ <b>SMS sent successfully</b> to <code>$phone</code>$sim:\n«$text»"
    override fun irSmsSendFailed(): String = "Failed to send SMS"
    override fun irSmsSendError(phone: String, err: String): String = "❌ <b>SMS sending error</b> to <code>$phone</code>:\n$err"
    override fun irNotifReplyCancelled(): String = "❌ Notification reply cancelled."
    override fun irReplyToAppSent(text: String): String = "✅ <b>Reply sent successfully to the app:</b>\n«$text»"
    override fun irReplySendFailed(): String = "Failed to send the reply"
    override fun irNotifReplyError(id: String, err: String): String = "❌ <b>Error replying to notification #$id:</b>\n$err"
    override fun irQuickReplySent(text: String): String = "✅ <b>Reply sent to the app:</b>\n«$text»"
    override fun irQuickReplyFailed(): String = "Failed to reply"
    override fun irQuickReplyError(err: String): String = "❌ <b>Notification reply error:</b> $err"
    override fun efBtnReply(): String = "💬 Reply"
    override fun efShakeAlert(): String = "🚨 <b>WARNING: The shake sensor has triggered!</b>\nThe device experienced a sharp acceleration/shake."
    override fun efSoundAlert(db: Int, threshold: Int, level: Int): String = "🚨 <b>WARNING: The sound detector has triggered!</b>\nNoise recorded at <b>$db dB</b> (threshold: <b>$threshold dB</b>, level $level/10)."
    override fun efMotionAlert(): String = "🚨 <b>WARNING: The motion detector has detected activity!</b>"
    override fun efChargingOn(): String = "<b>Connected to a charger</b>"
    override fun efChargingOff(): String = "<b>Disconnected from the charger</b>"
    override fun efLevel(level: Int): String = "• Charge level: <b>$level%</b>"
    override fun efSource(plug: String): String = "• Source: <b>$plug</b>"
    override fun efVoltage(mv: Int): String = "• Voltage: $mv mV"
    override fun efOnBattery(): String = "• Mode: <b>Running on battery (discharging)</b>"
    override fun efTemperature(temp: String): String = "• Temperature: ${temp}°C"
    override fun efLowAlert(level: Int): String = "🪫 <b>WARNING: Low battery!</b>\n\n• Current level: <b>$level%</b>\n• Status: <b>Discharging</b>\n• Recommendation: <i>Connect the device to a charger!</i>"
    override fun permName(id: String): String =
        when (id) {
            "root" -> "Root (Superuser)"
            "install_packages" -> "Install APK"
            "manage_storage" -> "All files (Storage)"
            "accessibility" -> "Accessibility Service"
            "write_settings" -> "System settings"
            "overlay" -> "Draw over other apps"
            "notification_listener" -> "Notification access"
            "sms" -> "SMS (Send/Read)"
            "calls" -> "Calls and call log"
            "dnd" -> "Do Not Disturb mode"
            "camera" -> "Camera"
            "microphone" -> "Microphone (audio recording)"
            "location" -> "Location (GPS)"
            "contacts" -> "Contacts"
            "battery_opt" -> "Doze Mode (unrestricted)"
            "post_notifications" -> "Notifications in the tray"
            "media_photos" -> "Photos and gallery"
            "media_audio" -> "Audio files"
            "bluetooth" -> "Bluetooth adapter"
            "data_saver" -> "Background traffic"
            else -> id
        }

    override fun permDesc(id: String): String =
        when (id) {
            "root" -> "Full superuser access (libsu)"
            "install_packages" -> "REQUEST_INSTALL_PACKAGES to update the app"
            "manage_storage" -> "MANAGE_EXTERNAL_STORAGE disk access"
            "accessibility" -> "Accessibility Service: click/USSD automation"
            "write_settings" -> "WRITE_SETTINGS auto-brightness control"
            "overlay" -> "SYSTEM_ALERT_WINDOW system dialogs"
            "notification_listener" -> "Intercepting incoming push notifications"
            "sms" -> "SEND_SMS, RECEIVE_SMS control via SMS"
            "calls" -> "CALL_PHONE, READ_CALL_LOG, READ_PHONE_STATE"
            "dnd" -> "ACCESS_NOTIFICATION_POLICY sound profiles"
            "camera" -> "CAMERA photo and video capture"
            "microphone" -> "RECORD_AUDIO hidden audio recording"
            "location" -> "ACCESS_FINE_LOCATION coordinates and tracking"
            "contacts" -> "READ_CONTACTS name resolution in calls/SMS"
            "battery_opt" -> "Ignoring battery optimization 24/7"
            "post_notifications" -> "POST_NOTIFICATIONS FGS foreground service"
            "media_photos" -> "Reading photos/videos from MediaStore"
            "media_audio" -> "Reading audio files for the player"
            "bluetooth" -> "BLUETOOTH_CONNECT Bluetooth control"
            "data_saver" -> "Operating under data saver mode"
            else -> ""
        }
    override fun efPlugLabel(plug: String): String =
        when (plug) {
            "AC (220V)" -> "AC (220V)"
            "USB port" -> "USB port"
            "Wireless charging" -> "Wireless charging"
            "Charger" -> "Charger"
            "Not connected" -> "Not connected"
            else -> plug
        }
}

object BotStringsRu : BotStrings {
    override fun startMenuTitle(version: String, rootStatus: String): String =
        "🤖 <b>Remote Bot Android</b> ($version)\n👑 <b>Root статус:</b> $rootStatus\n📡 <b>Служба:</b> 🟢 Активна"
    override fun startMenuSubtitle(): String = "Выберите категорию команд для навигации:"
    override fun btnBasic(): String = "⚙️ Базовые"
    override fun btnMedia(): String = "📷 Мультимедиа"
    override fun btnComm(): String = "📞 Связь и SMS"
    override fun btnGeoSec(): String = "📍 Геолокация"
    override fun btnFiles(): String = "📁 Файлы и App"
    override fun btnSystem(): String = "⚡ Система & Root"
    override fun btnAutomation(): String = "🤖 Автоматизация"
    override fun btnAllCommands(): String = "📋 Все команды"
    override fun btnStatus(): String = "📊 Статус"
    override fun btnBack(): String = "🔙 Назад в меню"
    override fun btnToggles(): String = "🎛️ Переключатели"
    override fun aboutTitle(version: String, rootStatus: String): String =
        """
        🤖 <b>Remote Bot Android</b>
        
        • <b>Версия:</b> $version
        • <b>Сборка:</b> Clean Architecture (SDK 35 / Android 15)
        • <b>Root статус:</b> $rootStatus
        
        🐙 <b>GitHub:</b> <a href="https://github.com/runalsh/remote-bot-android">github.com/runalsh/remote-bot-android</a>
        ✈️ <b>Telegram автора:</b> <a href="https://t.me/runalsh">@runalsh</a>
        
        ⚖️ Лицензия: Apache License 2.0
        """.trimIndent()

    override fun permissionsTitle(granted: Int, total: Int): String =
        "🛡️ <b>Статус Android-разрешений</b> ($granted/$total)"
    override fun filterLabel(filter: String): String = "Фильтр: <code>$filter</code>"
    override fun groupCriticalTitle(): String = "🔴 <b>Критические (Система/Сеть/Фон):</b>"
    override fun groupConfidentialTitle(): String = "🟡 <b>Конфиденциальные (Камера/Микрофон/СМС):</b>"
    override fun groupStandardTitle(): String = "🟢 <b>Стандартные (Базовые функции):</b>"
    override fun statusGranted(): String = "Выдано"
    override fun statusDenied(): String = "Отклонено"
    override fun noPermissionsFound(): String = "По заданному фильтру ничего не найдено."
    override fun permissionsUsageHint(): String = "💡 <i>Использование: /permissions [critical|confidential|standard|denied]</i>"

    override fun statusTitle(): String = "📊 <b>Сводный статус Remote Bot</b>"
    override fun statusServiceRunning(): String = "🤖 <b>Состояние службы:</b> 🟢 Активен"
    override fun statusRoot(granted: Boolean): String =
        "👑 <b>Root (Superuser):</b> " + if (granted) "✅ Доступен (UID 0)" else "❌ Не предоставлен"
    override fun statusConnectionMode(mode: String): String = "📡 <b>Режим связи:</b> <code>$mode</code>"
    override fun statusTelegramApiTitle(): String = "🌐 <b>Telegram Bot API:</b>"
    override fun statusActiveNode(status: String): String = "• <b>Активный узел:</b> $status"
    override fun statusStrategy(strategy: String): String = "• <b>Стратегия выбора:</b> <code>$strategy</code>"
    override fun statusEndpointsPool(): String = "• <b>Пул эндпоинтов:</b>"

    override fun incomingSmsTitle(from: String, sim: String): String =
        "📩 <b>Входящее SMS</b> (SIM: $sim)\n<b>От:</b> <code>$from</code>"
    override fun incomingCallTitle(from: String, sim: String): String =
        "📞 <b>Входящий звонок</b> (SIM: $sim)\n<b>От:</b> <code>$from</code>"
    override fun missedCallTitle(from: String, sim: String): String =
        "🔴 <b>Пропущенный вызов</b> (SIM: $sim)\n<b>От:</b> <code>$from</code>"
    override fun batteryChargingTitle(level: Int): String = "⚡ <b>Подключена зарядка</b> ($level%)"
    override fun batteryLowTitle(level: Int): String = "🪫 <b>Низкий заряд батареи</b> ($level%)"
    override fun batteryLevel(level: Int): String = "Уровень заряда: $level%"
    override fun actionApproved(): String = "✅ Подтвердить"
    override fun actionRejected(): String = "❌ Отклонить"
    override fun commandNotFound(cmd: String): String = "Неизвестная команда: $cmd"

    override fun categoryBase(): String =
        """
        ⚙️ <b>Базовые команды и Диагностика:</b>
        • /status — сводный статус подключения, пинг Telegram API, FCM/HMS
        • /permissions [filter] — аудит разрешений (critical/confidential/standard/denied)
        • /ping — проверка времени отклика и сетевой задержки
        • /echo &lt;текст&gt; — эхо-тест входящих команд бота
        • /chat_id — узнать Chat ID и User ID текущего чата
        • /about — информация о сборке бота, версии и разработчике
        • /uptime — время непрерывной работы процесса службы и системы Android
        """.trimIndent()

    override fun categoryMedia(): String =
        """
        📷 <b>Камера, Звук и Экран:</b>
        • /photo [front|back] — снимок с фронтальной или основной камеры
        • /videorec [front|back] [секунды] — скрытая видеозапись
        • /gallery [N] — отправка последних N медиафайлов из галереи
        • /rec [секунды] — скрытая аудиозапись с микрофона (голос)
        • /rec_file [секунды] — аудиозапись в виде несжатого файла .wav/.m4a
        • /tts &lt;текст&gt; — воспроизведение текста синтезатором речи на динамик
        • /player [play|pause|next|prev|stop] — управление воспроизведением медиаплеера
        • /volume [0-100|stream] [значение] — регулировка громкости звуковых потоков
        • /ring_mode [normal|silent|vibrate] — переключение профилей звонка
        • /device_search — громкая сирена и вибрация для поиска потерянного телефона
        • /screenshot — моментальный снимок экрана (MediaProjection / Root)
        • /screen_rec [секунды] — запись экрана в видеофайл (Root / Service)
        """.trimIndent()

    override fun categoryComm(): String =
        """
        📞 <b>Связь, SMS & Уведомления:</b>
        • /sms &lt;номер&gt; &lt;текст&gt; — прямая отправка исходящего SMS с выбором SIM
        • /sms_inbox [N] — просмотр последних входящих SMS сообщений
        • /call &lt;номер&gt; — инициация голосового телефонного вызова
        • /callshistory [N] — история входящих, исходящих и пропущенных вызовов
        • /ussd &lt;код&gt; — выполнение USSD-запроса баланса или тарифа (Android 8.0+)
        • /notify [list|clear] — просмотр активных системных уведомлений в шторке
        • /notify_send [id] — принудительная отправка уведомления в чат
        • /fake_notify &lt;title&gt; | &lt;text&gt; — генерация фиктивного уведомления в шторку Android
        • /answer &lt;notif_id&gt; &lt;текст&gt; — отправка быстрого ответа (RemoteInput) в мессенджер
        • /notify_clear [id|all] — удаление уведомления из шторки устройства
        • /notify_rule [add|del|list] — гибкие regex-правила фильтрации входящих уведомлений
        """.trimIndent()

    override fun categoryGeoSec(): String =
        """
        📍 <b>Геолокация & Охрана:</b>
        • /location — текущие координаты GPS/Сеть со ссылкой на карты Google
        • /track [start|stop|status] — непрерывный фоновый GPS-трекинг маршрута
        • /shake_detector [on|off|sens] — скрытая сигнализация на движение акселерометра
        • /detectmotion [on|off|front|back] — обнаружение движения камерой с авто-фото
        • /sound_detector [on|off|sens] — детекция шума/голоса вокруг смартфона
        """.trimIndent()

    override fun categoryFiles(): String =
        """
        📁 <b>Файлы & Приложения:</b>
        • /get_file &lt;путь&gt; — скачивание любого файла из файловой системы Android
        • /download &lt;url&gt; [путь] — удаленная загрузка файлов из интернета на устройство
        • /ls &lt;путь&gt; — просмотр содержимого папки на устройстве
        • /ls_interactive &lt;путь&gt; — интерактивный проводник файлов с Inline-кнопками навигации
        • /apps [system|user|all] — список установленных приложений и пакетов
        • /app_launch &lt;package&gt; — запуск выбранного приложения на экране
        • /install — удаленная установка APK-файла (тихая установка при Root)
        • /taskmanager (алиас: /top) — диспетчер запущенных процессов и потребления RAM
        • /kill &lt;package|pid&gt; — принудительное завершение зависшего приложения (Root)
        """.trimIndent()

    override fun categorySystem(): String =
        """
        ⚡ <b>Система & Root:</b>
        • /toggles (алиасы: /panel, /quick_settings) — интерактивная панель переключателей с кнопками
        • /system_info — сводка об устройстве, RAM, ROM, CPU, Root
        • /uptime — время непрерывной работы процесса службы и системы Android
        • /battery — статус аккумулятора, температура, вольтаж
        • /network — сетевые интерфейсы, IP (IPv4/IPv6), DNS, MAC, MTU
        • /wifi [on|off|toggle] — статус и переключение Wi-Fi
        • /bluetooth [on|off|toggle] — статус и переключение Bluetooth
        • /flashlight [on|off|toggle] — фонарик (вспышка камеры)
        • /airplane [on|off|toggle] — режим полета ⚡
        • /hotspot [on|off|toggle] (алиас: /tethering) — точка доступа Wi-Fi (Hotspot) ⚡
        • /mobile_data [on|off|toggle] (алиас: /data) — мобильная передача данных ⚡
        • /gps [on|off|toggle] (алиас: /location_mode) — геолокация (GPS) ⚡
        • /powersave [on|off|toggle] (алиас: /battery_saver) — режим экономии энергии ⚡
        • /auto_brightness [on|off|toggle] — автояркость экрана
        • /clipboard [текст|clear] — чтение и запись в буфер обмена
        • /sync [on|off|toggle] — автосинхронизация аккаунтов
        • /adb [on|off|toggle] (алиас: /usb_debugging) — режим отладки USB (ADB) ⚡
        • /adb_wifi [on|off|toggle|порт] (алиасы: /adbwifi, /wireless_adb) — отладка по Wi-Fi ⚡ (порт 5555)
        • /shell &lt;команда&gt; (алиас: /sh) — Linux терминал (Root/sh) ⚡
        • /reboot — перезагрузка устройства ⚡ (с паузой 10 сек)
        • /shutdown (алиас: /poweroff) — выключение смартфона ⚡ (с паузой 10 сек)
        • /recovery — перезагрузка в Recovery ⚡ (с паузой 10 сек)
        • /fastboot (алиас: /bootloader) — перезагрузка в Bootloader ⚡ (с паузой 10 сек)

        <i>⚡ = команды, требующие Root-прав (Superuser)</i>
        """.trimIndent()

    override fun categoryAuto(): String =
        """
        🤖 <b>Автоматизация, Tasker & Сеть:</b>
        • /tasker &lt;TaskName&gt; [par1] [par2] — запуск задач в приложении Tasker
        • /script &lt;код&gt; — выполнение системного скрипта/командной строки
        • /timers [list|add|del] — планировщик отложенных и периодических команд
        • /proxy — просмотр текущего статуса и подробная справка с примерами
        • /proxy [socks5|http|wss|off] — быстрая смена или выключение прокси
        • /active — активные фоновые операции
        • /stop_all — экстренная отмена всех операций
        • /log [N] [level] — журнал событий (по умолч: 20 строк, макс: 100, уровни: DEBUG/INFO/WARN/ERROR)
        • /backup — экспорт резервной копии конфигурации в файл .json
        • /restore_backup [json|файл] — восстановление конфигурации из текста или файла
        • /unsent_files — очередь неотправленных файлов
        • /bot_status — текущий статус Telegram и SMS каналов
        • /tg_bot [on|off|toggle] — включение/выключение Telegram бота
        • /sms_bot [on|off|toggle] — интерактивная панель статуса и включение/выключение SMS-бота
        • /bot_toggle &lt;tg|sms&gt; [on|off] — универсальный переключатель каналов связи
        • /add_user [id] / /del_user &lt;id&gt; / /list_users — управление белым списком Telegram User ID
        • /add_phone [номер] / /del_phone &lt;номер&gt; / /list_phones — доверенные SMS-номера без PIN
        • /add_forward [номер] / /del_forward &lt;номер&gt; / /list_forwards — номера пересылки SMS/звонков/push
        • /tg_forward [sms|calls|push|battery_charge|battery_low|battery] [on|off] — управление доставкой событий в Telegram
        • /events [on|off|notif|sms|calls|system|list|id] (алиас: /subs) — интерактивное меню и рассылка событий
        • /whitelist_user [add|del|clear|list|&lt;id&gt;] (алиас: /user) — полное управление списком Telegram ID
        • /whitelist_phone [add|del|clear|list|&lt;номер&gt;] (алиас: /phone) — полное управление доверенными номерами SMS
        • /sms_forward [add|del|clear|list|on|off|missed|push|translit|&lt;номер&gt;] (алиас: /forward) — комплексное управление пересылкой
        """.trimIndent()

    override fun fullHelp(version: String, rootText: String): String =
        """
        🤖 <b>Remote Bot Android</b> ($version)
        👑 Root статус: $rootText

        <b>${categoryBase()}</b>

        <b>${categoryMedia()}</b>

        <b>${categoryComm()}</b>

        <b>${categoryGeoSec()}</b>

        <b>${categoryFiles()}</b>

        <b>${categorySystem()}</b>

        <b>${categoryAuto()}</b>
        """.trimIndent()
    override fun radioUnknownCmd(cmd: String): String = "Неизвестная радио/периферийная команда: $cmd"
    override fun radioWifiNotFound(): String = "Wi-Fi адаптер не найден на устройстве."
    override fun radioBtNotFound(): String = "Bluetooth адаптер не найден на устройстве."
    override fun radioCamServiceUnavailable(): String = "Сервис камеры недоступен."
    override fun radioNoFlashCamera(): String = "Камера со вспышкой не найдена."
    override fun radioTorchFail(e: String): String = "Сбой управления фонариком: $e"
    override fun radioBtOnFail(): String = "⚠️ Не удалось включить Bluetooth (требуются Root права или разрешение)."
    override fun radioBtOffFail(): String = "⚠️ Не удалось выключить Bluetooth."
    override fun radioBtToggleFail(): String = "⚠️ Не удалось переключить Bluetooth."
    override fun radioInvalidParam(u: String): String = "Неверный параметр. Используйте: <code>$u</code>"
    override fun radioUsage(u: String): String = "Используйте: <code>$u</code>"
    override fun radioAirplaneRootFail(): String = "❌ Ошибка переключения режима полета через Root shell."
    override fun radioHotspotStartFail(): String = "❌ Ошибка запуска точки доступа."
    override fun radioHotspotStopFail(): String = "❌ Ошибка остановки точки доступа."
    override fun radioHotspotToggleFail(): String = "❌ Ошибка переключения точки доступа."
    override fun radioDataOnFail(): String = "❌ Ошибка включения мобильных данных."
    override fun radioDataOffFail(): String = "❌ Ошибка выключения мобильных данных."
    override fun radioDataToggleFail(): String = "❌ Ошибка переключения мобильных данных."
    override fun radioGpsOnFail(): String = "❌ Ошибка включения GPS."
    override fun radioGpsOffFail(): String = "❌ Ошибка выключения GPS."
    override fun radioGpsToggleFail(): String = "❌ Ошибка переключения GPS."
    override fun radioStateOnG(): String = "🟢 Включен"
    override fun radioStateOffR(): String = "🔴 Выключен"
    override fun radioStateOffW(): String = "⚪ Выключен"
    override fun radioStateAirplaneOn(): String = "✈️ Включен"
    override fun radioStateHotspotOn(): String = "📡 Включена"
    override fun radioStateHotspotOff(): String = "⚪ Выключена"
    override fun radioStateDataOn(): String = "🟢 Включена"
    override fun radioStateDataOff(): String = "🔴 Выключена"
    override fun radioStateGpsOn(): String = "📍 Включена"
    override fun radioStateGpsOff(): String = "⚪ Выключена"
    override fun radioNotConnected(): String = "Не подключен"
    override fun radioNoPerm(): String = "Нет разрешения"
    override fun radioWordOn(): String = "включен"
    override fun radioWordOff(): String = "выключен"
    override fun radioWordOnF(): String = "включена"
    override fun radioWordOffF(): String = "выключена"
    override fun radioWifiStatus(state: String, ssid: String, rssi: Int, speed: String, ip: String): String = "📶 <b>Статус Wi-Fi:</b> $state\n• Сеть (SSID): <code>$ssid</code>\n• Сигнал (RSSI): <code>$rssi dBm</code>\n• Скорость соединения: <code>$speed</code>\n• Локальный IP: <code>$ip</code>"
    override fun radioBtStatus(state: String, name: String, bonded: Int): String = "🦷 <b>Статус Bluetooth:</b> $state\n• Имя адаптера: <code>$name</code>\n• Сопряженных устройств: <code>$bonded</code>"
    override fun radioWifiOnOk(): String = "✅ <b>Wi-Fi успешно включен!</b>"
    override fun radioWifiOnFailLong(): String = "⚠️ Не удалось включить Wi-Fi.\nНа Android 10+ для прямого управления требуются Root-права (libsu)."
    override fun radioWifiOffOk(): String = "🔴 <b>Wi-Fi успешно выключен!</b>"
    override fun radioWifiOffFailLong(): String = "⚠️ Не удалось выключить Wi-Fi.\nНа Android 10+ для прямого управления требуются Root-права (libsu)."
    override fun radioWifiToggled(label: String): String = "🔄 <b>Wi-Fi переключен: $label</b>"
    override fun radioWifiToggleFailLong(): String = "⚠️ Не удалось переключить Wi-Fi.\nНа Android 10+ для прямого управления требуются Root-права (libsu)."
    override fun radioBtOnOk(): String = "✅ <b>Bluetooth успешно включен!</b>"
    override fun radioBtOffOk(): String = "🔴 <b>Bluetooth успешно выключен!</b>"
    override fun radioBtToggled(label: String): String = "🔄 <b>Bluetooth переключен: $label</b>"
    override fun radioTorchState(label: String): String = "🔦 <b>Фонарик:</b> $label"
    override fun radioTorchOnOk(): String = "🔦 <b>Фонарик включен!</b>"
    override fun radioTorchOffOk(): String = "💡 <b>Фонарик выключен!</b>"
    override fun radioTorchToggled(label: String): String = "🔄 <b>Фонарик $label</b>"
    override fun radioRootActive(): String = "🟢 Root активен"
    override fun radioRootNote(): String = "🔒 Требуются Root-права для изменения"
    override fun radioAirplaneStatus(state: String, note: String): String = "✈️ <b>Режим полета:</b> $state\n<i>$note</i>"
    override fun radioNeedRootAirplane(): String = "⚠️ Для режима полета требуются Root-права (Superuser)!\nПредоставьте Root в приложении на вкладке Диагностика."
    override fun radioAirplaneOnOk(): String = "✈️ <b>Режим полета включен!</b>"
    override fun radioAirplaneOffOk(): String = "🌐 <b>Режим полета выключен!</b>"
    override fun radioAirplaneToggled(label: String): String = "🔄 <b>Режим полета $label</b>"
    override fun radioHotspotStatus(state: String, note: String): String = "📡 <b>Точка доступа (Wi-Fi Hotspot):</b> $state\n<i>$note</i>"
    override fun radioNeedRootHotspot(): String = "⚠️ Для точки доступа требуются Root-права (Superuser)!\nПредоставьте Root в приложении на вкладке Диагностика."
    override fun radioHotspotOnOk(): String = "📡 <b>Точка доступа включена!</b>"
    override fun radioHotspotOffOk(): String = "⚪ <b>Точка доступа выключена!</b>"
    override fun radioHotspotToggled(label: String): String = "🔄 <b>Точка доступа $label</b>"
    override fun radioDataStatus(state: String, note: String): String = "📶 <b>Мобильная передача данных:</b> $state\n<i>$note</i>"
    override fun radioNeedRootData(): String = "⚠️ Для переключения мобильных данных требуются Root-права (Superuser)!\nПредоставьте Root в приложении на вкладке Диагностика."
    override fun radioDataOnOk(): String = "🟢 <b>Мобильные данные включены!</b>"
    override fun radioDataOffOk(): String = "🔴 <b>Мобильные данные выключены!</b>"
    override fun radioDataToggled(label: String): String = "🔄 <b>Мобильная передача данных $label</b>"
    override fun radioGpsStatus(state: String, note: String): String = "📍 <b>Геолокация (GPS):</b> $state\n<i>$note</i>"
    override fun radioNeedRootGps(): String = "⚠️ Для управления GPS требуются Root-права (Superuser)!\nПредоставьте Root в приложении на вкладке Диагностика."
    override fun radioGpsOnOk(): String = "📍 <b>Геолокация включена!</b>"
    override fun radioGpsOffOk(): String = "⚪ <b>Геолокация выключена!</b>"
    override fun radioGpsToggled(label: String): String = "🔄 <b>Геолокация $label</b>"
    override fun botLanguageCurrent(cur: String): String = "🌐 Текущий язык бота: <code>$cur</code>\nИзменить: <code>/bot_language auto|en|ru</code>"
    override fun botLanguageSet(v: String): String = "✅ Язык бота переключен на: <code>$v</code>"
    override fun botLanguageInvalid(v: String): String = "⚠️ Неизвестный язык '$v'. Используйте: auto, en, ru"
    override fun qsPanelBase(hasRoot: Boolean, wifiOn: Boolean, btOn: Boolean, flashOn: Boolean, autoBrightnessOn: Boolean, syncOn: Boolean): String = run { val _h = if (hasRoot) "🟢 Предоставлен (UID 0)" else "⚪ Обычный режим (без Root)"; val _w = if (wifiOn) "🟢 ВКЛ" else "⚪ ВЫКЛ"; val _b = if (btOn) "🟢 ВКЛ" else "⚪ ВЫКЛ"; val _f = if (flashOn) "🟢 ВКЛ" else "⚪ ВЫКЛ"; val _a = if (autoBrightnessOn) "☀️ ВКЛ" else "⚪ ВЫКЛ"; val _s = if (syncOn) "🔄 ВКЛ" else "⚪ ВЫКЛ"; "🎛 <b>Панель быстрых переключателей (Quick Settings)</b>\n<i>Статус Superuser: $_h</i>\n\n• Wi-Fi: $_w\n• Bluetooth: $_b\n• Фонарик: $_f\n• Автояркость: $_a\n• Синхронизация: $_s\n" }
    override fun qsPanelRoot(airplaneOn: Boolean, hotspotOn: Boolean, mobileDataOn: Boolean, gpsOn: Boolean, powerSaveOn: Boolean, adbOn: Boolean, adbWifiOn: Boolean, localIp: String, adbWifiPort: Int): String = run { val _a = if (airplaneOn) "✈️ ВКЛ" else "⚪ ВЫКЛ"; val _ho = if (hotspotOn) "📡 ВКЛ" else "⚪ ВЫКЛ"; val _m = if (mobileDataOn) "📶 ВКЛ" else "⚪ ВЫКЛ"; val _g = if (gpsOn) "📍 ВКЛ" else "⚪ ВЫКЛ"; val _p = if (powerSaveOn) "🔋 ВКЛ" else "⚪ ВЫКЛ"; val _u = if (adbOn) "🔌 ВКЛ" else "⚪ ВЫКЛ"; val _x = if (adbWifiOn) "📶 ВКЛ (<code>$localIp:$adbWifiPort</code>)" else "⚪ ВЫКЛ"; "• Режим полета: $_a ⚡\n• Точка доступа: $_ho ⚡\n• Моб. данные: $_m ⚡\n• Геолокация (GPS): $_g ⚡\n• Энергосбережение: $_p ⚡\n• Отладка по USB (ADB): $_u ⚡\n• Отладка по Wi-Fi: $_x ⚡\n" }
    override fun qsUnknownCmd(cmd: String): String = "Неизвестная команда системных настроек: $cmd"
    override fun qsOnWord(): String = "ВКЛ"
    override fun qsOffWord(): String = "ВЫКЛ"
    override fun ipNotFound(): String = "IP_НЕ_ОПРЕДЕЛЕН"
    override fun qsBtnTorch(on: Boolean): String = if (on) "🔦 Фонарик [ВКЛ]" else "🔦 Фонарик [ВЫКЛ]"
    override fun qsBtnSync(on: Boolean): String = if (on) "🔄 Синхр [ВКЛ]" else "🔄 Синхр [ВЫКЛ]"
    override fun qsBtnAirplane(on: Boolean): String = if (on) "✈️ Самолёт [ВКЛ] ⚡" else "✈️ Самолёт [ВЫКЛ] ⚡"
    override fun qsBtnData(on: Boolean): String = if (on) "📱 Данные [ВКЛ] ⚡" else "📱 Данные [ВЫКЛ] ⚡"
    override fun qsBtnSaver(on: Boolean): String = if (on) "🔋 Энерго [ВКЛ] ⚡" else "🔋 Энерго [ВЫКЛ] ⚡"
    override fun qsBtnBright(on: Boolean): String = if (on) "☀️ Автоярк [ВКЛ]" else "☀️ Автоярк [ВЫКЛ]"
    override fun qsBtnRefresh(): String = "🔄 Обновить панель"
    override fun qsRootTip(): String = "\n<i>💡 Предоставьте Root в приложении (вкладка Диагностика), чтобы разблокировать тумблеры: Самолёт, Hotspot, Моб. данные, GPS, Энергосбережение, USB/Wi-Fi Debugging.</i>\n"
    override fun psStateOn(): String = "🔋 Включен"
    override fun psStatus(state: String, note: String): String = "🔋 <b>Режим энергосбережения:</b> $state\n<i>$note</i>"
    override fun psNeedRoot(): String = "⚠️ Для управления энергосбережением требуются Root-права (Superuser)!\nПредоставьте Root в приложении на вкладке Диагностика."
    override fun psOnOk(): String = "🔋 <b>Режим энергосбережения включен!</b>"
    override fun psOnFail(): String = "❌ Ошибка включения энергосбережения."
    override fun psOffOk(): String = "⚪ <b>Режим энергосбережения выключен!</b>"
    override fun psOffFail(): String = "❌ Ошибка выключения энергосбережения."
    override fun psToggled(label: String): String = "🔄 <b>Режим энергосбережения $label</b>"
    override fun psToggleFail(): String = "❌ Ошибка переключения энергосбережения."
    override fun abStateOn(): String = "☀️ Включена"
    override fun abStateOff(): String = "⚪ Выключена (Ручная)"
    override fun abStatus(state: String): String = "☀️ <b>Автояркость экрана:</b> $state"
    override fun abOnOk(): String = "☀️ <b>Автояркость включена!</b>"
    override fun abOnFail(): String = "⚠️ Не удалось включить автояркость.\nТребуются права изменения системных настроек или Root."
    override fun abOffOk(): String = "⚪ <b>Автояркость выключена (ручной режим)!</b>"
    override fun abOffFail(): String = "⚠️ Не удалось выключить автояркость.\nТребуются права изменения системных настроек или Root."
    override fun abToggled(label: String): String = "🔄 <b>Автояркость $label</b>"
    override fun abToggleFail(): String = "⚠️ Не удалось переключить автояркость.\nТребуются права изменения системных настроек или Root."
    override fun syncStatus(state: String): String = "🔄 <b>Автосинхронизация аккаунтов:</b> $state"
    override fun syncOnOk(): String = "✅ <b>Автосинхронизация аккаунтов включена!</b>"
    override fun syncOffOk(): String = "🔴 <b>Автосинхронизация аккаунтов выключена!</b>"
    override fun syncToggled(label: String): String = "🔄 <b>Автосинхронизация аккаунтов $label!</b>"
    override fun cbUnavailable(): String = "Буфер обмена недоступен."
    override fun cbEmpty(): String = "📋 <b>Буфер обмена пуст.</b>"
    override fun cbClearBtn(): String = "🗑 Очистить буфер"
    override fun cbContent(t: String): String = "📋 <b>Содержимое буфера обмена:</b>\n<code>$t</code>"
    override fun cbSet(t: String): String = "📋 <b>Текст успешно скопирован в буфер устройства:</b>\n<code>$t</code>"
    override fun adbNeedRoot(): String = "❌ <b>Требуются права Superuser (Root)!</b>\nУправление режимом отладки USB (ADB) невозможно без прав root.\nПредоставьте доступ в приложении на вкладке «Диагностика»."
    override fun adbStatus(state: String, flag: Int): String = "🔌 <b>Режим отладки по USB (ADB):</b> $state\n• Доступ: <code>Root (Superuser)</code>\n• Глобальная настройка: <code>adb_enabled = $flag</code>"
    override fun adbOnOk(): String = "✅ <b>Режим отладки по USB (ADB) включен!</b> ⚡"
    override fun adbOffOk(): String = "🔴 <b>Режим отладки по USB (ADB) выключен.</b> ⚡"
    override fun adbUsage(): String = "Использование: <code>/adb [status|on|off|toggle]</code>"
    override fun awifiNeedRoot(): String = "❌ <b>Требуются права Superuser (Root)!</b>\nВключение отладки по Wi-Fi на системном уровне требует прав root.\nПредоставьте доступ в приложении на вкладке «Диагностика»."
    override fun awifiStateOn(port: Int): String = "🟢 Включена (порт $port)"
    override fun awifiStatusOn(state: String, ip: String, port: Int): String = "📶 <b>Режим отладки по Wi-Fi (Wireless ADB):</b> $state\n• Локальный IP: <code>$ip</code>\n• Порт: <code>$port</code>\n\n💡 <b>Команда подключения с ПК:</b>\n<code>adb connect $ip:$port</code>\n\n💡 <b>Команда отключения:</b>\n<code>adb disconnect $ip:$port</code>"
    override fun awifiStatusOff(state: String, ip: String): String = "📶 <b>Режим отладки по Wi-Fi (Wireless ADB):</b> $state\n• Порт по умолчанию: <code>5555</code>\n• Текущий IP: <code>$ip</code>\n\nИспользуйте кнопку ниже или команду <code>/adb_wifi on</code> для запуска ADB через TCP/IP."
    override fun awifiOnOkA(port: Int, ip: String): String = "✅ <b>Отладка по Wi-Fi успешно включена!</b> ⚡\n• Порт: <code>$port</code>\n• IP-адрес устройства: <code>$ip</code>\n\n💡 <b>Для подключения с компьютера выполните:</b>\n<code>adb connect $ip:$port</code>"
    override fun awifiOnOkB(port: Int, ip: String): String = "✅ <b>Отладка по Wi-Fi успешно включена!</b> ⚡\n• Порт: <code>$port</code>\n• IP-адрес: <code>$ip</code>\n\n💡 <b>Подключение:</b> <code>adb connect $ip:$port</code>"
    override fun awifiUsage(): String = "Использование: <code>/adb_wifi [status|on|off|toggle|порт]</code>\nПример: <code>/adb_wifi on 5555</code>"
    override fun auUnknownCmd(cmd: String): String = "Неизвестная аудио-команда: /$cmd"
    override fun auRecStopped(): String = "⏹ Запись микрофона остановлена. Сохраненное аудио отправляется..."
    override fun auNoActiveRec(): String = "⚠️ Нет активной записи микрофона для остановки."
    override fun auRecGoing(name: String): String = "🔴 <b>Идёт запись:</b> $name"
    override fun auRecIdle(): String = "⏹ <b>В ожидании</b> (нет активной записи)"
    override fun auRecPanel(status: String): String = "🎙 <b>Скрытая аудиозапись с микрофона (/rec):</b>\n• Статус: $status\n• Выберите длительность записи кнопками ниже (до 1 часа) или запустите длительную запись с возможностью остановки в любой момент."
    override fun auRecOpName(dur: String): String = "🎙 Аудиозапись микрофона ($dur)"
    override fun auRecDescFile(dur: String): String = "В виде файла (до $dur)"
    override fun auRecDescVoice(dur: String): String = "Голосовое сообщение (до $dur)"
    override fun auRecCaptionFile(sec: Int): String = "🎙 <b>Аудиозапись с микрофона</b> ($sec сек)"
    override fun auRecCaptionVoice(sec: Int): String = "🎙 <b>Запись с микрофона</b> ($sec сек)"
    override fun auRecError(e: String): String = "Ошибка аудиозаписи: $e"
    override fun auDurSec(s: Int): String = "$s сек"
    override fun auDurMin(m: Int): String = "$m мин"
    override fun auDurMinSec(m: Int, s: Int): String = "$m мин $s сек"
    override fun auBtnSec(n: String): String = "⏱ $n сек"
    override fun auBtnSecMin(n: String, m: String): String = "⏱ $n сек ($m)"
    override fun auBtnRecUntilStop(): String = "⏹ Запись до остановки"
    override fun auBtnStopRec(): String = "⏹ Остановить запись"
    override fun auBtnRefreshPanel(): String = "🔄 Обновить панель"
    override fun auBtnToVoice(): String = "🔄 Переключить на Voice"
    override fun auBtnToFile(): String = "🔄 Переключить на Файл"
    override fun auTtsUsage(): String = "🗣 <b>Синтез речи (TTS)</b>\nИспользование: <code>/tts [язык] &lt;текст&gt;</code>\nПримеры:\n• <code>/tts Привет, телефон на связи!</code>\n• <code>/tts en Hello</code>"
    override fun auTtsPlayed(t: String): String = "🗣 Воспроизведено через динамик:\n<i>«$t»</i>"
    override fun auTtsError(e: String): String = "Ошибка TTS: $e"
    override fun auBtnSirenOn(): String = "🔊 Сирена тревоги"
    override fun auBtnSirenOff(): String = "⏹ Выключить сирену"
    override fun auBtnRingNormal(): String = "🔔 Профиль: Звонок"
    override fun auBtnSilent(): String = "🔕 Без звука"
    override fun auBtnVibrate(): String = "📳 Вибро"
    override fun auBtnVolumeLevels(): String = "📊 Уровни громкости"
    override fun auBtnRefresh(): String = "🔄 Обновить"
    override fun auPlayerHead(mode: String): String = "🎵 <b>Медиаплеер и Звук</b>\n• Текущий звуковой профиль: <b>$mode</b>\n• Управление громкостью: <code>/volume [stream] [level]</code>\n• Синтез речи: <code>/tts &lt;текст&gt;</code>"
    override fun auVolHead(): String = "🔊 <b>Уровни громкости устройства</b>:\n\n"
    override fun auStreamRing(): String = "🔔 Вызов (Ring)"
    override fun auStreamNotif(): String = "💬 Уведомления"
    override fun auStreamAlarm(): String = "⏰ Будильник (Alarm)"
    override fun auStreamMedia(): String = "🎵 Медиа (Media)"
    override fun auStreamCall(): String = "📞 Разговор (Voice Call)"
    override fun auStreamSystem(): String = "⚙️ Система"
    override fun auVolHint(): String = "\n<i>Установка: <code>/volume &lt;media|ring|alarm|notif&gt; &lt;0..15&gt;</code></i>"
    override fun auBtnMedia50(): String = "🎵 Медиа 50%"
    override fun auBtnMedia100(): String = "🎵 Медиа 100%"
    override fun auBtnRing100(): String = "🔔 Вызов 100%"
    override fun auBtnAlarm100(): String = "⏰ Будильник 100%"
    override fun auVolSet(name: String, lvl: String, max: String): String = "🔊 Громкость потока <b>$name</b> установлена: <code>$lvl/$max</code>"
    override fun auVolError(e: String): String = "Ошибка установки громкости: $e"
    override fun auRingHead(mode: String): String = "🔔 <b>Звуковой профиль устройства</b>: <b>$mode</b>\n\n• <code>/ring_mode normal</code> — обычный режим со звуком\n• <code>/ring_mode silent</code> — без звука (DND / Тихий)\n• <code>/ring_mode vibrate</code> — только вибрация"
    override fun auBtnNormal(): String = "🔔 Обычный"
    override fun auRingToggled(icon: String, mode: String): String = "$icon Звуковой профиль переключен на: <b>$mode</b>"
    override fun auRingError(e: String): String = "Не удалось изменить звуковой режим (требуется разрешение Do Not Disturb): $e"
    override fun auSirenOff(): String = "⏹ <b>Поиск устройства</b>: сирена выключена."
    override fun auSirenOp(): String = "🚨 Сирена поиска устройства"
    override fun auSirenOpDesc(): String = "Громкость будильника 100%"
    override fun auSirenOn(): String = "🚨 <b>Включена сирена поиска устройства!</b>\nГромкость будильника установлена на 100%."
    override fun auSirenError(e: String): String = "Ошибка запуска сирены: $e"
    override fun auSdUsage(): String = "Формат: <code>/sound_detector</code> (интерактивная панель), <code>/sound_detector &lt;1..10&gt;</code> или <code>/sound_detector off</code>"
    override fun auSdOpName(lvl: String): String = "🔊 Детектор звука (Микрофон, $lvl/10)"
    override fun auSdOpDesc(db: String): String = "Порог $db dB"
    override fun auSdActive(db: String): String = "✅ <b>АКТИВЕН</b> (Порог: <b>$db dB</b>)"
    override fun auSdOff(): String = "❌ <b>ОТКЛЮЧЕН</b>"
    override fun auBtnSdOff(): String = "⏹ Выключить"
    override fun auBtnSdOn(lvl: String): String = "▶️ Включить ($lvl/10)"
    override fun auSdPanel(status: String, lvl: String, db: String): String = "🔊 <b>Охранный детектор звука (Микрофон)</b>\n• Статус: $status\n• Текущий уровень: <b>$lvl / 10</b> (Порог: $db dB)\n\n<i>Шкала: 1 = громкий шум (85 dB, крики/хлопки), 10 = ультрачувствительный (40 dB, тихий шепот/шорохи).</i>\nВыберите чувствительность кнопками ниже:"
    override fun auB60(): String = "⏱ 60 сек (1м)"
    override fun auB300(): String = "⏱ 300 сек (5м)"
    override fun auB600(): String = "⏱ 600 сек (10м)"
    override fun auB1800(): String = "⏱ 1800 сек (30м)"
    override fun auB3600(): String = "⏱ 3600 сек (1 час)"
    override fun secUnknownCmd(cmd: String): String = "Неизвестная команда безопасности: $cmd"
    override fun secUserListEmpty(): String = "<i>Список пуст (доступ разрешен всем или только первому подключившемуся)</i>"
    override fun secUserListBody(listStr: String): String = "👥 <b>Белый список пользователей Telegram (User ID):</b>\n\n$listStr\n\n<i>Управление:</i>\n<code>/add_user [id]</code> — добавить пользователя (по умолч. ваш ID)\n<code>/del_user &lt;id&gt;</code> — удалить пользователя\n<code>/whitelist_user clear</code> — очистить список"
    override fun secUserListCleared(): String = "⚠️ Белый список пользователей Telegram полностью очищен."
    override fun secAddUserIdHint(): String = "Укажите числовой User ID: <code>/add_user 123456789</code>"
    override fun secUserExists(id: String): String = "ℹ️ Пользователь <code>$id</code> уже есть в белом списке."
    override fun secUserAdded(id: String): String = "✅ Пользователь <code>$id</code> добавлен в белый список Telegram."
    override fun secDelUserIdHint(): String = "Укажите числовой User ID: <code>/del_user 123456789</code>"
    override fun secUserNotFound(id: String): String = "Пользователь <code>$id</code> не найден в списке."
    override fun secUserDeleted(id: String): String = "🗑 Пользователь <code>$id</code> удален из белого списка."
    override fun secAddDelUserFmt(): String = "Формат: <code>/add_user &lt;id&gt;</code> или <code>/del_user &lt;id&gt;</code>"
    override fun secUserIdTgHint(): String = "Укажите числовой Telegram User ID: <code>/add_user 123456789</code>"
    override fun secUserExistsTg(id: String): String = "ℹ️ Пользователь <code>$id</code> уже есть в белом списке Telegram."
    override fun secUserNotFoundTg(id: String): String = "Пользователь <code>$id</code> не найден в белом списке."
    override fun secUserDeletedTg(id: String): String = "🗑 Пользователь <code>$id</code> удален из белого списка Telegram."
    override fun secPhoneListEmpty(): String = "<i>Список пуст (управление без PIN отключено)</i>"
    override fun secPhoneListBody(listStr: String): String = "📱 <b>Доверенные номера SMS-управления (без PIN):</b>\n\n$listStr\n\n<i>Управление:</i>\n<code>/add_phone [номер]</code> — добавить номер (по умолч. ваш номер при SMS)\n<code>/del_phone &lt;номер&gt;</code> — удалить номер\n<code>/whitelist_phone clear</code> — очистить список"
    override fun secPhoneListCleared(): String = "⚠️ Доверенный список SMS-управления очищен."
    override fun secAddPhoneHint(): String = "Укажите номер: <code>/add_phone +79991234567</code>"
    override fun secPhoneExists(phone: String): String = "ℹ️ Номер <code>$phone</code> уже есть в доверенном списке SMS."
    override fun secPhoneAdded(phone: String): String = "✅ Номер <code>$phone</code> добавлен в доверенный список SMS-управления."
    override fun secDelPhoneHint(): String = "Укажите номер: <code>/del_phone +79991234567</code>"
    override fun secPhoneNotFound(phone: String): String = "Номер <code>$phone</code> не найден в доверенном списке."
    override fun secPhoneDeleted(phone: String): String = "🗑 Номер <code>$phone</code> удален из доверенного списка SMS."
    override fun secAddPhonePhoneHint(): String = "Укажите номер телефона: <code>/add_phone +79991234567</code>"
    override fun secPhoneNoPinAdded(phone: String): String = "✅ Номер <code>$phone</code> добавлен в доверенный список SMS-управления (без PIN)."
    override fun secDelPhonePhoneHint(): String = "Укажите номер телефона: <code>/del_phone +79991234567</code>"
    override fun secFwdListEmpty(): String = "<i>Список номеров назначения пуст (пересылка отключена)</i>"
    override fun secOnOff(on: Boolean): String = if (on) "Вкл ✅" else "Выкл ❌"
    override fun secSmsFwdOn(): String = "✅ Пересылка входящих SMS по сотовой связи включена."
    override fun secSmsFwdOff(): String = "⏹ Пересылка входящих SMS отключена."
    override fun secMissedFwd(enable: Boolean): String = "Пересылка пропущенных вызовов по SMS: ${if (enable) "Включена ✅" else "Отключена ⏹"}"
    override fun secPushFwd(enable: Boolean): String = "Пересылка Push-уведомлений по SMS: ${if (enable) "Включена ✅" else "Отключена ⏹"}"
    override fun secChargeFwd(enable: Boolean): String = "Пересылка статуса зарядки по SMS: ${if (enable) "Включена ✅" else "Отключена ⏹"}"
    override fun secLowBattFwd(enable: Boolean): String = "Пересылка низкого заряда батареи (≤20% каждые 3%) по SMS: ${if (enable) "Включена ✅" else "Отключена ⏹"}"
    override fun secAllBattFwd(enable: Boolean): String = "Все оповещения о батарее по SMS: ${if (enable) "Включены ✅" else "Отключены ⏹"}"
    override fun secTranslitFwd(enable: Boolean): String = "Транслитерация SMS (Латиница GOST): ${if (enable) "Включена ✅" else "Отключена ⏹"}"
    override fun secAddFwdHint(): String = "Укажите номер: <code>/add_forward +79991234567</code>"
    override fun secFwdExists(phone: String): String = "ℹ️ Номер <code>$phone</code> уже есть в списке пересылки SMS."
    override fun secFwdAdded(phone: String): String = "✅ Номер <code>$phone</code> добавлен в список пересылки SMS. Пересылка активирована!"
    override fun secDelFwdHint(): String = "Укажите номер: <code>/del_forward +79991234567</code>"
    override fun secFwdNotFound(phone: String): String = "Номер <code>$phone</code> не найден в списке пересылки."
    override fun secFwdDeleted(phone: String): String = "🗑 Номер <code>$phone</code> удален из списка пересылки."
    override fun secFwdCleared(): String = "⚠️ Список номеров пересылки SMS очищен."
    override fun secFwdAddedShort(phone: String): String = "✅ Номер <code>$phone</code> добавлен для пересылки SMS!"
    override fun secAddFwdPhoneHint(): String = "Укажите номер телефона: <code>/add_forward +79991234567</code>"
    override fun secFwdAddedCalls(phone: String): String = "✅ Номер <code>$phone</code> добавлен в список пересылки SMS/звонков. Пересылка активирована!"
    override fun secDelFwdPhoneHint(): String = "Укажите номер телефона: <code>/del_forward +79991234567</code>"
    override fun secEventsEmpty(text: String): String = "ℹ️ Белый список пользователей пуст. Меню ваших подписок:\n\n$text"
    override fun secSubsHead(): String = "📋 <b>Статус рассылки событий по пользователям:</b>\n\n"
    override fun secSelfTag(): String = " (Вы)"
    override fun secRoleCmds(): String = "🕹 <i>Только команды</i>"
    override fun secRoleRecv(): String = "📬 <i>Получатель</i>"
    override fun secEventsHint(): String = "<i>Управление: <code>/events [id]</code> или <code>/events &lt;on|off&gt;</code></i>"
    override fun secUnknownCategory(c: String): String = "Неизвестная категория: $c. Доступно: notif, sms, calls, system, on, off"
    override fun secStateLabel(on: Boolean): String = if (on) "включена 🟢" else "выключена 🔴"
    override fun secEventsOffSelf(id: String): String = "🔕 Рассылка всех событий для вашего аккаунта (<code>$id</code>) <b>выключена</b>.\nВы переведены в роль <b>Только управление</b> (все команды доступны, фоновый спам отключен)."
    override fun secEventsOnSelf(id: String): String = "🔔 Рассылка основных событий для вашего аккаунта (<code>$id</code>) <b>включена</b> (push-уведомления, SMS, звонки)."
    override fun secEventsOffUser(id: String): String = "🔕 Рассылка событий для пользователя <code>$id</code> выключена (режим «Только команды»)."
    override fun secEventsOnUser(id: String): String = "🔔 Рассылка событий для пользователя <code>$id</code> включена."
    override fun secCategoryForUser(c: String, id: String, st: String): String = "✅ Категория <b>$c</b> для <code>$id</code> $st."
    override fun secCategorySelf(c: String, st: String): String = "✅ Категория <b>$c</b> для вашего аккаунта $st."
    override fun secEventsUsage(): String = "Формат команды:\n• <code>/events</code> — открыть интерактивное меню\n• <code>/events list</code> — список подписок пользователей\n• <code>/events &lt;on|off&gt;</code> — включить/выключить всё\n• <code>/events &lt;notif|sms|calls|system&gt; &lt;on|off&gt;</code> — переключить категорию\n• <code>/events &lt;user_id&gt; [on|off]</code> — настройка конкретного пользователя"
    override fun secFwdListBody(listStr: String, s1: String, s2: String, s3: String, s4: String, s5: String, s6: String): String = "📤 <b>Номера пересылки SMS и Уведомлений:</b>\n\n$listStr\n\n• Пересылка SMS: $s1\n• Пересылка пропущенных звонков: $s2\n• Пересылка Push: $s3\n• Статус зарядки/питания: $s4\n• Низкий заряд (≤20% каждые 3%): $s5\n• Транслитерация: $s6\n\n<i>Управление:</i>\n<code>/add_forward [номер]</code> — добавить номер назначения\n<code>/del_forward &lt;номер&gt;</code> — удалить номер назначения\n<code>/sms_forward on|off</code> — вкл/выкл пересылку SMS\n<code>/sms_forward missed on|off</code> — вкл/выкл пересылку звонков\n<code>/sms_forward push on|off</code> — вкл/выкл пересылку Push\n<code>/sms_forward battery_charge on|off</code> — вкл/выкл статус зарядки\n<code>/sms_forward battery_low on|off</code> — вкл/выкл низкий заряд (≤20% каждые 3%)\n<code>/sms_forward battery on|off</code> — вкл/выкл все оповещения батареи\n<code>/sms_forward translit on|off</code> — вкл/выкл транслитерацию\n<code>/sms_forward clear</code> — очистить все номера"
    override fun secDelUserIdTgHint(): String = "Укажите числовой Telegram User ID: <code>/del_user 123456789</code>"
    override fun secPhoneNotFoundTg(phone: String): String = "Номер <code>$phone</code> не найден в доверенном списке SMS."
    override fun locUnknownCmd(cmd: String): String = "Неизвестная команда геолокации: $cmd"
    override fun locAcquiring(t: String): String = "🛰️ Определение местоположения... Это может занять до $t секунд."
    override fun locOpName(t: String): String = "🛰️ Поиск геолокации (${t}с)"
    override fun locOpDesc(t: String): String = "Определение GPS/LBS координат (таймаут ${t}с)"
    override fun locAcquireError(e: String): String = "❌ Ошибка определения координат: $e"
    override fun locTrackInactive(): String = "ℹ️ Периодический трекинг в данный момент не активен."
    override fun locTrackOpName(r: String): String = "🛰️ Периодический GPS-трекинг ($r)"
    override fun locTrackOpDesc(r: String): String = "Интервал $r"
    override fun locTrackPanelState(act: Boolean): String = if (act) "🟢 <b>АКТИВЕН</b>" else "⏹ <b>ОСТАНОВЛЕН</b>"
    override fun locTrackPanelStateInt(act: Boolean, i: String): String = if (act) "🟢 <b>АКТИВЕН</b> ($i)" else "⏹ <b>ОСТАНОВЛЕН</b>"
    override fun locDurSec(s: Long): String = "$s сек"
    override fun locDurHour(h: Long): String = "$h ч"
    override fun locDurMin(m: Long): String = "$m мин"
    override fun locDurMinSec(m: Long, s: Long): String = "$m мин $s сек"
    override fun locStateGps(): String = "🛰️ Спутниковый GPS/ГЛОНАСС"
    override fun locStateLbs(): String = "📡 Косвенные признаки (Вышки сотовой связи / Wi-Fi)"
    override fun locSignalDefault(): String = "Уверенный приём"
    override fun locSignalGps(lvl: String): String = "📶 <b>Сигнал GPS:</b> $lvl"
    override fun locSignalGpsSats(used: String, total: String): String = "📶 <b>Сигнал GPS:</b> $used в решении / $total видимых"
    override fun locSignalGpsLabel(): String = "📶 <b>Сигнал GPS:</b> "
    override fun locSignalNoGps(): String = "❌ Спутниковый сигнал отсутствует (поиск по LBS/Wi-Fi)"
    override fun locSeqStr(n: String): String = " [Точка #$n]"
    override fun locTitleTracked(seq: String): String = "🛰️ <b>Периодический трекинг геопозиции$seq:</b>"
    override fun locTitleDevice(): String = "📍 <b>Геолокация устройства:</b>"
    override fun locAltitudeSpeed(alt: String, spd: String): String = "⛰️ <b>Высота:</b> $alt м | 🏃 <b>Скорость:</b> $spd км/ч"
    override fun locCoord(lat: String, lon: String): String = "🌐 <b>Координаты:</b> <code>$lat, $lon</code>"
    override fun locAccuracy(a: String): String = "🎯 <b>Точность:</b> ±$a м"
    override fun locMethod(m: String): String = "📡 <b>Способ определения:</b> $m"
    override fun locTimeAddr(t: String, a: String): String = "🕒 <b>Время фиксации:</b> $t$a"
    override fun locLinks(g: String, o: String): String = "🗺️ <a href=\"$g\">Google Карты</a> | <a href=\"$o\">OpenStreetMap</a>"
    override fun locAddrBlock(a: String): String = "\n🏠 <b>Адрес:</b> $a"
    override fun locBtnAgain(): String = "🔄 Запросить снова"
    override fun locBtnInstant(): String = "⚡ Мгновенно (кэш)"
    override fun locBtnStartTrack(): String = "🛰️ Запустить трекинг (/track)"
    override fun locBtnStopTrack(): String = "⏹ Остановить трекинг"
    override fun locBtnStatus(): String = "📊 Статус"
    override fun locBtnSingle(): String = "📍 Разовый запрос (/location)"
    override fun locBtn30s(): String = "⏱ 30 сек"
    override fun locBtn1m(): String = "⏱ 1 мин"
    override fun locBtn5m(): String = "⏱ 5 мин"
    override fun locBtn15m(): String = "⏱ 15 мин"
    override fun locBtn30m(): String = "⏱ 30 мин"
    override fun locBtn60m(): String = "⏱ 60 мин"
    override fun locGpsOn(): String = "✅ <b>ВКЛЮЧЕН</b>"
    override fun locGpsOff(): String = "❌ <b>ВЫКЛЮЧЕН</b>"
    override fun locGpsStatus(st: String): String = "🛰️ <b>GPS модуль:</b> $st\nToggle: <code>/gps on</code> | <code>/gps off</code>"
    override fun locGpsOnOff(on: Boolean): String = if (on) "включен ✅" else "выключен ❌"
    override fun locGpsToggled(st: String): String = "🛰️ GPS модуль успешно $st"
    override fun locGpsToggleError(e: String): String = "❌ Ошибка переключения GPS: $e"
    override fun locGpsUsage(): String = "Формат: <code>/gps on</code> или <code>/gps off</code>"
    override fun locShakeOpName(lvl: String, cd: String): String = "📳 Датчик тряски ($lvl/10, ${cd}с)"
    override fun locShakeOpDesc(th: String, cd: String): String = "Порог $th m/s², интервал ${cd}с"
    override fun locShakeCdLabel(s: String, act: Boolean): String = if (act) "${s}s ✅" else "${s}s"
    override fun locShakeBtnOff(): String = "⏹ Выключить"
    override fun locShakeBtnOn(lvl: String, cd: String): String = "▶️ Включить ($lvl/10, ${cd}с)"
    override fun locShakeBtnRefresh(): String = "🔄 Обновить"
    override fun locShakeActive(th: String, cd: String): String = "✅ <b>АКТИВЕН</b> (Порог: $th m/s², Интервал: ${cd}с)"
    override fun locShakeOff(): String = "❌ <b>ОТКЛЮЧЕН</b>"
    override fun locTrackStopped(points: String, m: String, s: String): String = "⏹ <b>Периодический трекинг остановлен.</b>\n\n• Отправлено точек: <b>$points</b>\n• Время работы трекера: <b>${m}м ${s}с</b>"
    override fun locTrackStatus(st: String, iv: String, points: String, dur: String): String = "📊 <b>Статус периодического трекинга (/track):</b>\n\n• Состояние: $st\n• Интервал отправки: <b>$iv</b>\n• Отправлено точек: <b>$points</b>\n• Время работы: <b>$dur</b>"
    override fun locTrackStarted(r: String): String = "🛰️ <b>Периодический трекинг запущен!</b>\n\n⏱ Интервал отправки: <b>каждые $r</b>\n📍 Координаты будут периодически отправляться в этот чат до остановки.\n\n<i>Для остановки нажмите кнопку ниже или отправьте команду <code>/track stop</code>.</i>"
    override fun locTrackPanel(st: String): String = "🛰️ <b>Управление периодическим трекингом (/track):</b>\n\n• Текущий статус: $st\n• Выберите интервал отправки геопозиции с помощью кнопок:"
    override fun locShakePanel(st: String, lvl: String, th: String, cd: String): String = "📳 <b>Охранный датчик тряски (Акселерометр)</b>\n\n• Статус: $st\n• Текущий уровень: <b>$lvl / 10</b> (Порог: $th m/s²)\n• Интервал отправки тревоги: <b>$cd сек</b> (защита от спама)\n\n<i>Работает непрерывно до явной остановки. При фиксации тряски отправляет тревогу не чаще выбранного интервала.</i>\n<i>Шкала: 1 = грубый (30 m/s², сильные удары/падения), 10 = ультрачувствительный (1 m/s², малейшее движение).</i>\n\n👇 <b>Чувствительность (1..10):</b>"
    override fun locResultBody(coords: String, acc: String, method: String, time: String, addr: String, gmaps: String, osm: String): String = "🌐 <b>Координаты:</b> <code>$coords</code>\n🎯 <b>Точность:</b> ±$acc м\n📡 <b>Способ определения:</b> $method\n🕒 <b>Время фиксации:</b> $time$addr\n🗺️ <a href=\"$gmaps\">Google Карты</a> | <a href=\"$osm\">OpenStreetMap</a>"
    override fun locSatsFragment(used: String, total: String): String = " | 🛰️ Спутники: $used в решении / $total видимых"

    override fun bkpUnknownCmd(cmd: String): String = "❌ Неизвестная команда бэкапа: $cmd"
    override fun bkpFileCaption(): String = "💾 <b>Резервная копия конфигурации Remote Bot</b>\n\n<i>Для восстановления отправьте этот файл боту или выполните:</i>\n<code>/restore_backup &lt;json&gt;</code>"
    override fun bkpJsonBlock(json: String): String = "💾 <b>Резервная копия конфигурации:</b>\n\n<pre><code class=\"language-json\">\n$json\n</code></pre>\n\n<i>Для восстановления отправьте команду:</i>\n<code>/restore_backup &lt;json&gt;</code>"
    override fun bkpRestoreHelp(): String = "📥 <b>Восстановление конфигурации Remote Bot</b>\n\nДоступно два способа:\n1. <b>Отправить файл</b>: просто отправьте файл <code>.json</code> с резервной копией в этот чат (можно с подписью <code>/restore_backup</code>).\n2. <b>Вставить текст</b>: отправьте команду с содержимым JSON:\n<code>/restore_backup &lt;json-текст&gt;</code>"
    override fun bkpRestored(version: String): String = "✅ <b>Конфигурация успешно восстановлена</b> из резервной копии (v$version)!"
    override fun bkpUnknownError(): String = "Неизвестная ошибка"
    override fun bkpDeserializeError(err: String): String = "❌ Ошибка десериализации бэкапа: $err"
    override fun pxStatusOff(): String = "🌐 <b>Текущий статус:</b> Выключен (Прямое соединение без прокси)"
    override fun pxAuthWith(user: String): String = "с авторизацией (<code>$user</code>)"
    override fun pxAuthNone(): String = "без авторизации"
    override fun pxWsPath(path: String): String = "\n• WSS путь: <code>$path</code>"
    override fun pxStatusBlock(type: String, addr: String, auth: String, extra: String): String = "🌐 <b>Текущий статус:</b>\n• Тип: <b>$type</b>\n• Адрес: <code>$addr</code>\n• Авторизация: $auth$extra"
    override fun pxHelp(status: String): String = "$status\n\n━━━━━━━━━━━━━━━━━━━━━\n📖 <b>Управление и примеры команд:</b>\n\n1️⃣ <b>SOCKS5 прокси:</b>\n<code>/proxy socks5 &lt;host&gt; &lt;port&gt; [login] [password]</code>\n<i>Пример без пароля:</i>\n<code>/proxy socks5 192.168.1.100 1080</code>\n<i>Пример с авторизацией:</i>\n<code>/proxy socks5 1.2.3.4 10808 myuser mypass</code>\n\n2️⃣ <b>HTTP прокси:</b>\n<code>/proxy http &lt;host&gt; &lt;port&gt; [login] [password]</code>\n<i>Пример:</i>\n<code>/proxy http 192.168.1.50 8080 admin secret</code>\n\n3️⃣ <b>WSS WebSocket туннель:</b>\n<code>/proxy wss &lt;host&gt; &lt;port&gt; [path]</code>\n<i>Пример:</i>\n<code>/proxy wss myproxy.example.com 443 /telegram-ws</code>\n\n4️⃣ <b>Отключение прокси:</b>\n<code>/proxy off</code>"
    override fun pxDisabled(): String = "🌐 Прокси отключен. Используется прямое соединение."
    override fun pxFormat(sub: String, tail: String): String = "Формат: /proxy $sub &lt;host&gt; &lt;port&gt; $tail"
    override fun pxBadPort(port: String): String = "Некорректный порт: $port"
    override fun pxSet(type: String, addr: String): String = "✅ Прокси переключен на <b>$type</b> ($addr)"
    override fun pxWssSet(url: String): String = "✅ Установлен <b>WSS-туннель</b> ($url)"
    override fun pxUnknownParam(sub: String, available: String): String = "Неизвестный параметр /proxy: $sub. Доступно: $available"
    override fun shUnknownCmd(cmd: String): String = "❌ Неизвестная shell-команда: $cmd"
    override fun shHelp(): String = "💻 <b>Linux Shell Терминал</b>\n\nИспользование: <code>/shell &lt;команда&gt;</code>\nПримеры:\n• <code>/shell uname -a</code>\n• <code>/shell df -h</code>\n• <code>/shell getprop ro.build.version.release</code>\n• <code>/shell ip addr show</code>\n• <code>/shell ls -la /sdcard</code>"
    override fun shNoOutput(code: Int): String = "(Команда завершилась без текстового вывода, код: $code)"
    override fun shTruncated(limit: Int): String = "\n\n⚠️ [Вывод превысил лимит, показаны первые $limit символов]"
    override fun shResultBlock(emoji: String, mode: String, cmd: String, code: Int, out: String): String = "$emoji <b>[$mode] $cmd</b>\n<i>Код завершения: $code</i>\n\n<pre>$out</pre>"
    override fun shTimeout(): String = "⏱ Таймаут: выполнение команды заняло более 30 секунд и было прервано."
    override fun shExecError(msg: String): String = "❌ Ошибка выполнения: $msg"
    override fun shDelayedFooter(status: String): String = "$status\n<i>⏳ Выполнение произойдет через 10 секунд...</i>"
    override fun shRootRestart(): String = "❌ Для перезагрузки требуются права Superuser (Root). Предоставьте Root в Magisk/KernelSU/APatch."
    override fun shRootShutdown(): String = "❌ Для выключения требуются права Superuser (Root). Предоставьте Root в Magisk/KernelSU/APatch."
    override fun shRootRecovery(): String = "❌ Для перехода в Recovery требуются права Superuser (Root). Предоставьте Root в Magisk/KernelSU/APatch."
    override fun shRootBootloader(): String = "❌ Для перехода в Bootloader требуются права Superuser (Root). Предоставьте Root в Magisk/KernelSU/APatch."
    override fun shStatusReboot(): String = "🔄 <b>Устройство будет перезагружено</b>"
    override fun shStatusShutdown(): String = "⏻ <b>Устройство будет выключено</b>"
    override fun shStatusRecovery(): String = "🛠 <b>Устройство будет перезагружено в режим Recovery</b>"
    override fun shStatusFastboot(): String = "⚡ <b>Устройство будет перезагружено в режим Bootloader (Fastboot)</b>"
    override fun faUnknownCmd(cmd: String): String = "❌ Неизвестная команда управления файлами/приложениями: $cmd"
    override fun faGetFileUsage(): String = "Формат: <code>/get_file &lt;путь_к_файлу_или_папке&gt; [zip]</code>\nПример: <code>/get_file /sdcard/DCIM/Camera/photo.jpg</code>\nПример для папки: <code>/get_file /sdcard/Documents zip</code>"
    override fun faArchiveCaption(name: String, size: String): String = "📦 Архив: <code>$name</code> ($size)"
    override fun faZipError(msg: String): String = "❌ Ошибка создания zip-архива: $msg"
    override fun faFileCaption(name: String, size: String): String = "📄 Файл: <code>$name</code> ($size)"
    override fun faGetFileError(msg: String): String = "❌ Ошибка получения файла: $msg"
    override fun faDownloadUsage(): String = "Формат: <code>/download &lt;URL&gt; [путь_назначения]</code>\nПример: <code>/download https://example.com/file.apk</code>"
    override fun faDownloaded(path: String, size: String): String = "✅ <b>Файл успешно скачан:</b>\n<code>$path</code> ($size)"
    override fun faDownloadError(msg: String): String = "❌ Ошибка скачивания файла: $msg"
    override fun faDirEmpty(path: String): String = "📁 Каталог пуст: <code>$path</code>"
    override fun faDirContentHeader(path: String): String = "📁 <b>Содержимое каталога:</b> <code>$path</code>\n\n"
    override fun faMoreItems(count: Int): String = "\n<i>... и еще $count объектов</i>"
    override fun faDirReadError(msg: String): String = "❌ Ошибка чтения директории: $msg"
    override fun faBtnUp(): String = "⬆️ Вверх"
    override fun faBtnRefresh(): String = "🔄 Обновить"
    override fun faBtnZip(): String = "📦 Скачать ZIP"
    override fun faExplorerHeader(path: String, dirs: Int, files: Int, total: Int): String = "📂 <b>Проводник:</b> <code>$path</code>\n<i>Папок: $dirs, Файлов: $files (Всего: $total)</i>\n\n👉 <i>Клик по папке — переход внутрь. Клик по файлу — отправка в чат.</i>"
    override fun faNoApps(): String = "📱 Приложений не найдено."
    override fun faAppsHeader(count: Int): String = "📱 <b>Установленные приложения ($count):</b>\n\n"
    override fun faMoreApps(count: Int): String = "<i>... и еще $count приложений. Используйте фильтр: /apps &lt;поиск&gt;</i>\n"
    override fun faLaunchHint(): String = "\n<i>Запуск: /app_launch &lt;package&gt;</i>"
    override fun faLaunchUsage(): String = "Формат: <code>/app_launch &lt;package_name&gt;</code>\nПример: <code>/app_launch com.android.settings</code>"
    override fun faAppLaunched(pkg: String): String = "🚀 <b>Приложение запущено:</b> <code>$pkg</code>"
    override fun faLaunchError(msg: String): String = "❌ Ошибка запуска: $msg"
    override fun faInstallHelp(): String = "📦 <b>Установка APK-пакетов</b>\n\nДоступно два способа:\n1. <b>Отправить файл в чат</b>: просто прикрепите и отправьте <code>.apk</code> файл боту (можно с подписью <code>/install</code>). Бот скачает его и установит автоматически.\n2. <b>Указать локальный путь</b>: \n<code>/install &lt;путь_к_apk&gt;</code>\n<i>Пример:</i> <code>/install /sdcard/Download/app.apk</code>"
    override fun faApkInstalled(path: String): String = "✅ <b>APK пакет успешно установлен:</b>\n<code>$path</code>"
    override fun faInstallError(msg: String): String = "❌ Ошибка установки APK: $msg"
    override fun faNoProcesses(): String = "⚙️ Нет активных процессов."
    override fun faTaskManagerHeader(): String = "⚙️ <b>Диспетчер процессов (TaskManager):</b>\n\n"
    override fun faKillHint(): String = "\n<i>Завершить: /kill &lt;PID&gt; или /kill &lt;package&gt;</i>"
    override fun faProcessListError(msg: String): String = "❌ Не удалось получить список процессов: $msg"
    override fun faKillUsage(): String = "Формат: <code>/kill &lt;PID&gt;</code> или <code>/kill &lt;package_name&gt;</code>\nПример: <code>/kill 1234</code> или <code>/kill com.example.app</code>"
    override fun faProcessKilled(target: String): String = "🛑 Процесс / приложение <code>$target</code> принудительно остановлено."
    override fun faKillError(msg: String): String = "❌ Ошибка завершения: $msg"
    override fun faUnsentEmpty(): String = "📤 Очередь неотправленных файлов пуста."
    override fun faUnsentHeader(count: Int): String = "📤 <b>Очередь неотправленных файлов ($count):</b>\n\n"
    override fun faUnsentLine(path: String, type: String, attempts: Int): String = "• <code>$path</code> (Тип: $type, Попыток: $attempts)"
    override fun tnUnknownCmd(cmd: String): String = "❌ Неизвестная команда телефонии: $cmd"
    override fun tnSmsUsage(): String = "Формат: <code>/sms [sim] &lt;номер&gt; &lt;текст&gt;</code>\nПример: <code>/sms +79991234567 Привет!</code>\nПример для SIM2: <code>/sms 2 +79991234567 Привет!</code>"
    override fun tnSmsNeedArgs(): String = "Укажите номер телефона и текст сообщения.\nПример: <code>/sms +79991234567 Привет!</code>"
    override fun tnSmsSent(phone: String, sim: String, text: String): String = "📨 <b>SMS успешно отправлено</b> на номер <code>$phone</code>$sim:\n«$text»"
    override fun tnSmsError(msg: String): String = "❌ Ошибка отправки SMS: $msg"
    override fun tnSmsInboxEmpty(): String = "📭 Входящих SMS не найдено."
    override fun tnSmsInboxHeader(limit: Int): String = "📬 <b>Последние SMS ($limit):</b>\n\n"
    override fun tnSmsReadError(msg: String): String = "❌ Не удалось прочитать SMS: $msg"
    override fun tnCallUsage(): String = "Формат: <code>/call [sim] &lt;номер&gt;</code>\nПример: <code>/call +79991234567</code>"
    override fun tnCallNeedNumber(): String = "Укажите номер телефона для вызова."
    override fun tnCallInitiated(phone: String, sim: String): String = "📞 <b>Инициирован исходящий звонок</b> на номер <code>$phone</code>$sim..."
    override fun tnCallError(msg: String): String = "❌ Ошибка при совершении звонка: $msg"
    override fun tnCallsEmpty(): String = "📞 Журнал вызовов пуст."
    override fun tnCallsHeader(limit: Int): String = "📞 <b>Журнал звонков (последние $limit):</b>\n\n"
    override fun tnCallDuration(min: Long, sec: Long): String = " [$min м $sec с]"
    override fun tnCallMeta(time: String, type: String): String = "   <i>$time | Тип: $type</i>"
    override fun tnCallsReadError(msg: String): String = "❌ Не удалось получить журнал вызовов: $msg"
    override fun tnUssdUsage(): String = "Формат: <code>/ussd [sim] &lt;код&gt;</code>\nПример: <code>/ussd *100#</code>"
    override fun tnUssdNeedCode(): String = "Укажите USSD-код, например <code>*100#</code>"
    override fun tnUssdResponse(sim: String, code: String, response: String): String = "📟 <b>USSD Ответ$sim для <code>$code</code>:</b>\n\n<code>$response</code>"
    override fun tnUssdError(msg: String): String = "❌ Ошибка выполнения USSD-запроса: $msg"
    override fun tnNotifEmpty(): String = "🔔 Активных перехваченных уведомлений нет.\n<i>Убедитесь, что боту предоставлен доступ в Настройки -> Доступ к уведомлениям.</i>"
    override fun tnNotifHeader(limit: Int): String = "🔔 <b>Последние уведомления ($limit):</b>\n\n"
    override fun tnNotifReplyBadge(id: String): String = " [💬 <i>Ответ: /answer $id текст</i>]"
    override fun tnNotifyUsage(): String = "Формат: <code>/notify &lt;заголовок&gt; &lt;текст&gt;</code>\nПример: <code>/notify Внимание Важное сообщение</code>"
    override fun tnNotifyPosted(title: String, text: String): String = "🔔 <b>Уведомление отправлено в статус-бар смартфона:</b>\nЗаголовок: <b>$title</b>\nТекст: $text"
    override fun tnNotifyError(msg: String): String = "❌ Ошибка отправки уведомления: $msg"
    override fun tnFakeNotifyUsage(): String = "Формат: <code>/fake_notify &lt;пакет&gt; &lt;заголовок&gt; &lt;текст&gt;</code>\nПример: <code>/fake_notify ru.sberbankmobile СберБанк Перевод +5000р от Диана</code>\nПример 2: <code>/fake_notify com.whatsapp Диана Привет, как дела?</code>"
    override fun tnAppSber(): String = "СберБанк"
    override fun tnAppTbank(): String = "Т-Банк"
    override fun tnFakeNotifySimulated(app: String, title: String, text: String): String = "🧪 <b>Сэмулировано входящее уведомление от [$app]:</b>\nОт: <b>$title</b>\nТекст: $text"
    override fun tnFakeNotifyError(msg: String): String = "❌ Ошибка эмуляции уведомления: $msg"
    override fun tnAnswerUsage(): String = "Формат: <code>/answer &lt;ID&gt; &lt;текст ответа&gt;</code>\nПример: <code>/answer 1 Привет, скоро буду!</code>"
    override fun tnAnswerSent(id: String, text: String): String = "💬 <b>Ответ успешно отправлен</b> в уведомление #$id:\n«$text»"
    override fun tnAnswerError(msg: String): String = "❌ Не удалось отправить ответ: $msg"
    override fun tnActUsage(): String = "Формат: <code>/notify_act &lt;ID&gt; [номер_действия]</code>\nПример: <code>/notify_act 1 0</code>"
    override fun tnActDone(index: Int, id: String): String = "⚡ <b>Действие (индекс $index) успешно выполнено</b> для уведомления #$id!"
    override fun tnActError(msg: String): String = "❌ Ошибка выполнения действия: $msg"
    override fun tnClearUsage(): String = "Формат: <code>/notify_clear &lt;ID/all&gt;</code>"
    override fun tnCleared(id: String): String = "🗑️ Уведомление #$id удалено."
    override fun tnClearError(msg: String): String = "❌ Ошибка удаления уведомления: $msg"
    override fun tnRulesEmpty(): String = "📋 <b>Правила фильтрации уведомлений пусты.</b>\nДобавить: <code>/notify_rule add &lt;pkg&gt; [pattern]</code>"
    override fun tnRulesHeader(): String = "📋 <b>Правила фильтрации уведомлений:</b>\n\n"
    override fun tnRuleAllow(): String = "✅ Разрешать"
    override fun tnRuleExclude(): String = "⛔ Исключать"
    override fun tnRuleAdded(id: String): String = "✅ Добавлено правило фильтрации #$id"
    override fun tnRuleDeleted(id: String): String = "🗑️ Правило #$id удалено."
    override fun tnRulesUsage(): String = "Формат: <code>/notify_rule list</code> или <code>/notify_rule add exclude &lt;pkg&gt;</code> или <code>/notify_rule del &lt;id&gt;</code>"
    override fun mdUnknownCmd(cmd: String): String = "❌ Неизвестная мультимедиа-команда: /$cmd"
    override fun mdLensFront(): String = "Фронтальная"
    override fun mdLensBack(): String = "Основная"
    override fun mdLensFrontSelfie(): String = "Фронтальная (селфи)"
    override fun mdLensBackRear(): String = "Основная (задняя)"
    override fun mdLensFrontLower(): String = "фронтальная"
    override fun mdLensBackLower(): String = "основная"
    override fun mdFlashOn(): String = "Вспышка: ВКЛ"
    override fun mdFlashOff(): String = "Вспышка: ВЫКЛ"
    override fun mdPhotoCaption(lens: String, flash: String, rotation: String): String = "📷 <b>Фотография с камеры</b>\nКамера: $lens\n$flash\nПоворот: $rotation°"
    override fun mdPhotoError(msg: String): String = "Ошибка съемки фото: $msg"
    override fun mdRecStopped(): String = "⏹ Видеозапись камеры остановлена. Идет обработка и отправка..."
    override fun mdNoActiveRec(): String = "⚠️ Нет активной видеозаписи для остановки."
    override fun mdRecActive(name: String): String = "🔴 <b>Идёт видеозапись:</b> $name"
    override fun mdRecIdle(): String = "⏹ <b>В ожидании</b> (нет активной записи)"
    override fun mdRecPanel(status: String, lens: String): String = "📹 <b>Скрытая видеозапись с камеры (/videorec):</b>\n\n• Статус: $status\n• Выбранная камера: <b>$lens</b>\n• Выберите длительность кнопками ниже (до 1 часа) или запустите длительную запись с возможностью остановки в любой момент:"
    override fun mdRecOpName(lens: String, dur: String): String = "📹 Видеозапись камеры ($lens, $dur)"
    override fun mdRecOpDesc(lens: String, dur: String): String = "Камера $lens, длительность до $dur"
    override fun mdRecCaption(lens: String, sec: Int): String = "📹 <b>Видеозапись с камеры</b> ($lens, $sec сек)"
    override fun mdRecError(msg: String): String = "Ошибка видеозаписи: $msg"
    override fun mdDurSec(sec: Int): String = "$sec сек"
    override fun mdDurMin(min: Int): String = "$min мин"
    override fun mdDurMinSec(min: Int, sec: Int): String = "$min мин $sec сек"
    override fun mdBtnToggleToBack(): String = "🔄 Переключить на Основную"
    override fun mdBtnToggleToFront(): String = "🔄 Переключить на Фронтальную"
    override fun mdBtnDur(sec: Int): String = "⏱ $sec сек"
    override fun mdBtnDurMin(sec: Int, min: Int): String = "⏱ $sec сек ($min м)"
    override fun mdBtnDurHour(sec: Int): String = "⏱ $sec сек (1 час)"
    override fun mdBtnRecUntilStop(): String = "🔴 Запись до остановки"
    override fun mdBtnStopRec(): String = "⏹ Остановить видеозапись"
    override fun mdBtnRefreshPanel(): String = "🔄 Обновить панель"
    override fun mdBtnStop(): String = "⏹ Стоп"
    override fun mdBtnDisable(): String = "⏹ Выключить"
    override fun mdBtnEnable(level: Int, cooldown: Int): String = "▶️ Включить ($level/10, $cooldown с)"
    override fun mdBtnRefresh(): String = "🔄 Обновить"
    override fun mdSec(sec: Int): String = "$sec с"
    override fun mdSecActive(sec: Int): String = "$sec с ✅"
    override fun mdMotionActive(percent: Int, cooldown: Int): String = "✅ <b>АКТИВЕН</b> (Порог: $percent%, Интервал: $cooldown с)"
    override fun mdMotionInactive(): String = "❌ <b>ОТКЛЮЧЕН</b>"
    override fun mdMotionPanel(status: String, level: Int, percent: Int, cooldown: Int): String = "👁 <b>Детектор движения (Камера)</b>\n\n• Статус: $status\n• Чувствительность: <b>$level / 10</b> ($percent%)\n• Интервал отправки фото: <b>$cooldown сек</b> (защита от спама)\n\n<i>Работает непрерывно до явной остановки. При обнаружении движения бот отправляет тревогу с фото не чаще выбранного интервала.</i>\n<i>Шкала чувствительности: 1 (10%) — крупные движения, 10 (100%) — малейшие колебания.</i>\n\n👇 <b>Чувствительность (1..10):</b>"
    override fun mdMotionOpName(level: Int, cooldown: Int): String = "👁 Детектор движения камеры ($level/10, $cooldown с)"
    override fun mdMotionOpDesc(percent: Int, cooldown: Int): String = "Порог $percent%, интервал $cooldown с"
    override fun mdGalleryEmpty(): String = "🖼 <b>Галерея</b>: новых фотографий в памяти не обнаружено."
    override fun mdGalleryCaption(count: Int): String = "🖼 <b>Последние $count фото из галереи</b>"
    override fun siUnknownCmd(cmd: String): String = "❌ Неизвестная команда системной информации: $cmd"
    override fun siUptimeDays(d: Long, h: Long, m: Long): String = "${d}д ${h}ч ${m}м"
    override fun siUptimeHours(h: Long, m: Long): String = "${h}ч ${m}м"
    override fun siNetCellular(): String = "Мобильная сеть (LTE/5G)"
    override fun siNetOff(): String = "Отключена"
    override fun siRootGranted(): String = "🟢 Предоставлен (UID 0)"
    override fun siRootDenied(): String = "🔴 Нет / Отказано"
    override fun siSummary(model: String, os: String, build: String, patch: String, root: String, uptime: String, cores: Int, abis: String, ramUsed: Long, ramTotal: Long, ramPct: Int, romAvail: String, romTotal: String, screen: String, sensors: Int, netType: String, ip: String): String = "📱 <b>Сводка системы (System Info)</b>\n\n<b>Устройство:</b>\n• Модель: <code>$model</code>\n• ОС: <code>$os</code>\n• Сборка: <code>$build</code>\n• Патч безопасности: <code>$patch</code>\n• Root доступ: <b>$root</b>\n• Аптайм: <code>$uptime</code>\n\n<b>Аппаратная часть:</b>\n• CPU: <code>$cores ядер ($abis)</code>\n• RAM: <code>$ramUsed / $ramTotal МБ ($ramPct% занято)</code>\n• ROM: <code>$romAvail ГБ свободно из $romTotal ГБ</code>\n• Экран: <code>$screen</code>\n• Датчики: <code>$sensors доступно</code>\n\n<b>Связь и Сеть:</b>\n• Тип подключения: <code>$netType</code>\n• Локальный IP: <code>$ip</code>"
    override fun siBtnTogglesPanel(): String = "🎛 Панель переключателей"
    override fun siBtnBattery(): String = "🔋 Батарея"
    override fun siBtnNetworkInterfaces(): String = "🌐 Сетевые интерфейсы"
    override fun siSysInfoError(msg: String): String = "Ошибка получения системной информации: $msg"
    override fun siBatteryReadError(): String = "Не удалось прочитать состояние батареи."
    override fun siBatCharging(): String = "⚡ Заряжается"
    override fun siBatDischarging(): String = "🔋 Разряжается"
    override fun siBatFull(): String = "🟢 Полный заряд (100%)"
    override fun siBatNotCharging(): String = "⚪ Не заряжается"
    override fun siBatUnknown(): String = "Неизвестно"
    override fun siPlugAc(): String = "Сеть 220V (AC)"
    override fun siPlugUsb(): String = "USB порт"
    override fun siPlugWireless(): String = "Беспроводная зарядка"
    override fun siPlugBattery(): String = "От батареи (без кабеля)"
    override fun siHealthGood(): String = "✅ Отличное (Good)"
    override fun siHealthOverheat(): String = "🔥 Перегрев (Overheat)"
    override fun siHealthDead(): String = "💀 Неисправна (Dead)"
    override fun siHealthOverVoltage(): String = "⚠️ Перенапряжение"
    override fun siHealthCold(): String = "❄️ Переохлаждение"
    override fun siHealthNormal(): String = "Нормальное"
    override fun siVoltage(volts: String, mv: Int): String = "$volts В ($mv мВ)"
    override fun siBatteryBlock(icon: String, pct: Int, status: String, plug: String, temp: String, voltage: String, health: String, tech: String): String = "$icon <b>Состояние аккумулятора (Battery)</b>\n\n• Уровень заряда: <b>$pct%</b>\n• Статус: <b>$status</b>\n• Источник питания: <code>$plug</code>\n• Температура: <code>$temp</code>\n• Напряжение: <code>$voltage</code>\n• Здоровье батареи: <code>$health</code>\n• Технология: <code>$tech</code>"
    override fun siPowerSaveOn(): String = "🔋 Энергосбережение [ВКЛ]"
    override fun siPowerSaveOff(): String = "🔋 Энергосбережение [ВЫКЛ]"
    override fun siBtnRefresh(): String = "🔄 Обновить"
    override fun siBatParamsError(msg: String): String = "Сбой чтения параметров батареи: $msg"
    override fun siNoInterfaces(): String = "🌐 <b>Сетевые интерфейсы не найдены.</b>"
    override fun siIfaceCellular(): String = "Мобильная сеть (LTE/5G) 📱"
    override fun siIfaceAbsent(): String = "Отсутствует ⚪"
    override fun siNetTitle(): String = "🌐 <b>Сетевые интерфейсы устройства</b>\n\n"
    override fun siNetPrimary(transport: String): String = "• <b>Основное подключение:</b> $transport\n"
    override fun siNoAddresses(): String = "  • IP: <i>нет адресов</i>\n"
    override fun siBtnMobileData(): String = "📱 Моб. данные"
    override fun siNetError(msg: String): String = "Сбой получения сетевых интерфейсов: $msg"
    override fun siUptimeText(app: String, os: String): String = "⏱️ <b>Время непрерывной работы (Uptime)</b>\n\n🤖 <b>Служба бота:</b> <code>$app</code>\n📱 <b>Система Android:</b> <code>$os</code>"
    override fun siNA(): String = "Н/Д"
    override fun siBtnStatus(): String = "📊 Статус"
    override fun siBtnToggles(): String = "🎛️ Переключатели"
    override fun siUnitD(): String = "д "
    override fun siUnitH(): String = "ч "
    override fun siUnitM(): String = "м "
    override fun siUnitS(): String = "с"
    override fun scuBtnDisableRoot(): String = "🔒 Выключить (Root)"
    override fun scuBtnEnableRoot(): String = "🔒 Включить (Root)"
    override fun scuBtnDisableBolt(): String = "🔴 Выключить ⚡"
    override fun scuBtnEnableBolt(): String = "🟢 Включить ⚡"
    override fun scuBtnDisable(): String = "🔴 Выключить"
    override fun scuBtnEnable(): String = "🟢 Включить"
    override fun scuBtnAllToggles(): String = "🎛 Все тумблеры"
    override fun scUnknownCmd(cmd: String): String = "❌ Неизвестная команда системного контроля: $cmd"
    override fun srUnknownCmd(cmd: String): String = "❌ Неизвестная команда экрана: /$cmd"
    override fun srScreenshotCaption(method: String): String = "🖥 <b>Снимок экрана</b>\nМетод: $method"
    override fun srScreenshotError(msg: String): String = "Сбой захвата экрана: $msg"
    override fun srRecOpName(sec: Int): String = "📹 Запись экрана ($sec сек)"
    override fun srRecOpDesc(): String = "Фоновая запись экрана в MP4"
    override fun srRecCaption(sec: Int): String = "📹 <b>Видеозапись экрана</b> ($sec сек)"
    override fun srRecError(msg: String): String = "Сбой записи экрана: $msg"
    override fun autUnknownCmd(cmd: String): String = "❌ Неизвестная команда автоматизации: $cmd"
    override fun autTaskerInstalled(): String = "✅ Установлен"
    override fun autTaskerMissing(): String = "⚠️ Не обнаружен (установите Tasker)"
    override fun autTaskerHelp(state: String): String = "🤖 <b>Интеграция с Tasker:</b> $state\n\nИспользование: <code>/tasker &lt;TaskName&gt; [par1] [par2]</code>\nПример: <code>/tasker NightMode 1 on</code>"
    override fun autParams(params: String): String = " (Параметры: $params)"
    override fun autTaskerSent(task: String, extra: String): String = "✅ <b>Tasker задача отправлена на исполнение:</b> <code>$task</code>$extra"
    override fun autTaskerError(msg: String): String = "❌ Ошибка отправки задачи в Tasker: $msg"
    override fun autScriptUsage(): String = "Формат: <code>/script &lt;код_скрипта&gt;</code>\nПример: <code>/script date +%Y-%m-%d</code>\nПример: <code>/script echo 'Hello world'</code>"
    override fun autScriptOk(ms: Long, out: String): String = "⚡ <b>Результат выполнения скрипта ($ms ms):</b>\n\n<code>$out</code>"
    override fun autScriptErr(ms: Long, err: String, out: String): String = "❌ <b>Ошибка выполнения ($ms ms):</b>\n$err\n\nВывод:\n<code>$out</code>"
    override fun autNoTimers(): String = "⏱ <b>Активных таймеров нет.</b>\nСоздать: <code>/timers add 60s /photo</code> или <code>/timers add 10m /location</code>"
    override fun autTimersHeader(count: Int): String = "⏱ <b>Запланированные таймеры ($count):</b>\n\n"
    override fun autIntervalRepeat(sec: Long): String = " [повтор каждые $sec с]"
    override fun autIntervalOnce(): String = " [однократно]"
    override fun autTimerLine(id: String, cmd: String, time: String, interval: String): String = "• <b>ID: $id</b> | <code>$cmd</code>\n   <i>Сработает в $time$interval</i>\n\n"
    override fun autTimerDelHint(): String = "<i>Удалить таймер: /timers del &lt;ID&gt;</i>"
    override fun autBadDelay(delay: String): String = "Неверный формат времени задержки: <code>$delay</code> (примеры: 30s, 5m, 2h)"
    override fun autTimerCreated(id: String, cmd: String, delay: String): String = "✅ <b>Таймер #$id создан!</b>\nКоманда <code>$cmd</code> выполнится через $delay."
    override fun autTimerDeleted(id: String): String = "🗑️ Таймер #$id удален."
    override fun autTimersUsage(): String = "Формат: <code>/timers list</code> или <code>/timers add &lt;время&gt; &lt;команда&gt;</code> или <code>/timers del &lt;ID&gt;</code>"
    override fun evStatusOn(): String = "🟢 Вкл"
    override fun evStatusOff(): String = "🔴 Выкл"
    override fun evRoleCommandsOnly(): String = "🕹 <b>Только управление</b> (рассылка отключена)"
    override fun evRoleRecipient(): String = "📬 <b>Получатель событий</b>"
    override fun evUserSelf(id: Long): String = "вашего аккаунта (ID: <code>$id</code>)"
    override fun evUserOther(id: Long): String = "пользователя (ID: <code>$id</code>)"
    override fun evText(user: String, role: String, notif: String, sms: String, calls: String, system: String): String = "⚙️ <b>Рассылка событий для $user</b>\n\nРоль: $role\n\n• 🔔 Push-уведомления: <b>$notif</b>\n• 💬 Входящие SMS: <b>$sms</b>\n• 📞 Звонки / Пропущенные: <b>$calls</b>\n• ⚙️ Системные (батарея, питание, датчики): <b>$system</b>\n\n<i>Нажимайте кнопки ниже для переключения категорий:</i>"
    override fun evBtnNotifOn(): String = "🔔 Уведомления: [ВКЛ]"
    override fun evBtnNotifOff(): String = "🔕 Уведомления: [ВЫКЛ]"
    override fun evBtnSmsOn(): String = "💬 SMS: [ВКЛ]"
    override fun evBtnSmsOff(): String = "🚫 SMS: [ВЫКЛ]"
    override fun evBtnCallsOn(): String = "📞 Звонки: [ВКЛ]"
    override fun evBtnCallsOff(): String = "📵 Звонки: [ВЫКЛ]"
    override fun evBtnSystemOn(): String = "⚙️ Система: [ВКЛ]"
    override fun evBtnSystemOff(): String = "💤 Система: [ВЫКЛ]"
    override fun evBtnAllOff(): String = "🔕 Только команды (Выкл всё)"
    override fun evBtnAllOn(): String = "🔔 Включить всё"
    override fun evBtnRefresh(): String = "🔄 Обновить"
    override fun bsRootAvail(): String = "✅ Доступен (UID 0)"
    override fun bsRootMissing(): String = "❌ Отсутствует / Не выдан"
    override fun bsRootActive0(): String = "✅ АКТИВЕН (UID 0)"
    override fun bsRootRestricted(): String = "⚠️ ОГРАНИЧЕН (без Root)"
    override fun bsApiOk(ms: Long): String = "🟢 Доступен (${ms}ms)"
    override fun bsApiErr(err: String): String = "🔴 Ошибка ($err)"
    override fun bsTimeout(): String = "🔴 Таймаут"
    override fun bsTimeoutWord(): String = "Таймаут"
    override fun bsLatency(ms: Long): String = "🟢 ${ms}ms"
    override fun bsUnreachable(): String = "🔴 Недоступен"
    override fun bsEndpointItem(root: String): String = "  • <code>$root</code>"
    override fun bsIndented(value: String): String = "  <code>$value</code>"
    override fun bsPushTitle(): String = "🚀 <b>Сетевая доступность Push сервисов:</b>"
    override fun bsGwLine(status: String): String = "• <b>Webhook Push Gateway:</b> $status"
    override fun bsFcmLine(status: String): String = "• <b>Google FCM:</b> $status (<code>fcm.googleapis.com</code>)"
    override fun bsHmsLine(status: String): String = "• <b>Huawei HMS:</b> $status (<code>push-api.cloud.huawei.com</code>)"
    override fun bsPing(ms: Long): String = "🏓 Pong! Задержка обработки: ${ms}ms"
    override fun bsEchoEmpty(): String = "Пустой ввод."
    override fun bsEchoPrefix(): String = "📢 Эхо: "
    override fun bsUnknownCmd(cmd: String): String = "❌ Неизвестная сервисная команда: $cmd"
    override fun bsPermProviderMissing(): String = "Провайдер разрешений не инициализирован."
    override fun bsPermLine(icon: String, name: String, state: String): String = "$icon <b>$name:</b> $state"
    override fun bsRefreshStatus(): String = "🔄 Обновить статус"
    override fun bmUnknownCmd(cmd: String): String = "❌ Неизвестная команда управления: $cmd"
    override fun bmNoOps(): String = "⚙️ Нет активных фоновых операций."
    override fun bmOpsHeader(count: Int): String = "⚙️ <b>Активные фоновые операции ($count):</b>\n\n"
    override fun bmOpDuration(sec: Long): String = "   • Длительность: ${sec}s"
    override fun bmOpInfo(desc: String): String = "   • Инфо: $desc"
    override fun bmStopHint(): String = "\nДля экстренной остановки используйте /stop_all"
    override fun bmStopped(count: Int): String = "🛑 Прервано активных фоновых операций: <b>$count</b>"
    override fun bmLogEmpty(note: String): String = "📜 Журнал логов пуст$note."
    override fun bmLogFilterNote(level: String): String = " с уровнем >= $level"
    override fun bmLogLevelHeader(level: String): String = " (уровень >= $level)"
    override fun bmLogHeader(count: Int, header: String): String = "📜 <b>Последние $count записей лога$header:</b>\n\n"
    override fun bmLogUsage(): String = "\n<i>Использование: /log [N] [TRACE|DEBUG|INFO|WARN|ERROR]</i>"
    override fun bmUnsentEmpty(): String = "📁 <b>Очередь неотправленных файлов:</b>\nОчередь пуста. Все файлы доставлены."
    override fun bmActive(): String = "✅ Активен"
    override fun bmDisabled(): String = "❌ Выключен"
    override fun bmInlineOn(): String = "Вкл ✅"
    override fun bmInlineOff(): String = "Выкл ❌"
    override fun bmStatusPanel(tg: String, mode: String, sms: String, tgSms: String, tgCalls: String, tgPush: String, tgCharge: String, tgLow: String, smsSms: String, smsCalls: String, smsPush: String, smsCharge: String, smsLow: String, smsTranslit: String, targets: Int): String = "🤖 <b>Статус каналов и пересылки Remote Bot:</b>\n\n• <b>Telegram Бот:</b> $tg (режим: <code>$mode</code>)\n  - Пересылка SMS: $tgSms\n  - Пересылка звонков: $tgCalls\n  - Пересылка Push: $tgPush\n  - Статус зарядки/питания: $tgCharge\n  - Низкий заряд (≤20% каждые 3%): $tgLow\n\n• <b>SMS Бот:</b> $sms\n  - Пересылка SMS: $smsSms\n  - Пересылка звонков: $smsCalls\n  - Пересылка Push: $smsPush\n  - Статус зарядки/питания: $smsCharge\n  - Низкий заряд (≤20% каждые 3%): $smsLow\n  - Транслитерация: $smsTranslit\n  - Номеров пересылки: $targets\n\n<i>Управление:</i>\n<code>/tg_bot [on|off]</code> — включить/выключить Telegram\n<code>/sms_bot [on|off]</code> — включить/выключить SMS канал\n<code>/tg_forward [sms|calls|push|battery_charge|battery_low|battery] [on|off]</code> — пересылка в Telegram\n<code>/sms_forward [on|off|missed|push|battery_charge|battery_low|battery|translit]</code> — пересылка по SMS"
    override fun bmTgForwardPanel(sms: String, calls: String, push: String, charge: String, low: String): String = "✈️ <b>Пересылка событий в Telegram:</b>\n• Входящие SMS: $sms\n• Звонки: $calls\n• Push-уведомления: $push\n• Статус зарядки и питания: $charge\n• Низкий заряд (≤20% каждые 3%): $low\n\n<i>Управление:</i>\n<code>/tg_forward sms on|off</code> — пересылка SMS в Telegram\n<code>/tg_forward calls on|off</code> — пересылка звонков в Telegram\n<code>/tg_forward push on|off</code> — пересылка Push в Telegram\n<code>/tg_forward battery_charge on|off</code> — оповещения о подключении/отключении ЗУ\n<code>/tg_forward battery_low on|off</code> — оповещения о разряде батареи (≤20% каждые 3%)\n<code>/tg_forward battery on|off</code> — вкл/выкл все оповещения батареи"
    override fun bmFwdTgSms(state: String): String = "Пересылка входящих SMS в Telegram: $state"
    override fun bmFwdTgCalls(state: String): String = "Пересылка звонков в Telegram: $state"
    override fun bmFwdTgPush(state: String): String = "Пересылка Push-уведомлений в Telegram: $state"
    override fun bmFwdTgCharge(state: String): String = "Оповещения о статусе зарядки и питания в Telegram: $state"
    override fun bmFwdTgLow(state: String): String = "Оповещения о низком заряде (≤20% каждые 3%) в Telegram: $state"
    override fun bmFwdTgBattery(state: String): String = "Все оповещения о батарее и зарядке в Telegram: $state"
    override fun bmTgForwardUnknownType(): String = "Неизвестный тип. Доступны: sms, calls, push, battery_charge, battery_low, battery. Пример: <code>/tg_forward battery_low on</code>"
    override fun bmTgBotState(state: String): String = "🤖 <b>Telegram-бот</b> сейчас $state.\nИспользуйте: <code>/tg_bot on</code> или <code>/tg_bot off</code>"
    override fun bmTgBotUsage(): String = "Формат: <code>/tg_bot on</code> или <code>/tg_bot off</code>"
    override fun bmTgBotResult(state: String): String = "🤖 <b>Telegram-бот</b> $state"
    override fun bmSmsBotState(state: String): String = "💬 <b>SMS-бот</b> сейчас $state.\nИспользуйте: <code>/sms_bot on</code> или <code>/sms_bot off</code>"
    override fun bmSmsBotUsage(): String = "Формат: <code>/sms_bot on</code> или <code>/sms_bot off</code>"
    override fun bmSmsBotResult(state: String): String = "💬 <b>SMS-бот</b> $state"
    override fun bmBotToggleUsage(): String = "Формат: <code>/bot_toggle &lt;tg|sms&gt; [on|off]</code>"
    override fun bmUnknownChannel(channel: String): String = "Неизвестный канал: $channel. Доступны: tg, sms"
    override fun bmStateOn(): String = "включен ✅"
    override fun bmStateOff(): String = "выключен ❌"
    override fun bmEnabledFem(): String = "Включена ✅"
    override fun bmDisabledFem(): String = "Отключена ⏹"
    override fun bmEnabledPlur(): String = "Включены ✅"
    override fun bmDisabledPlur(): String = "Отключены ⏹"
    override fun bmTgOnRes(): String = "включен ✅ (прием команд возобновлен)"
    override fun bmTgOffRes(): String = "выключен ❌ (опрос команд приостановлен)"
    override fun bmSmsOnRes(): String = "включен ✅ (прием SMS команд и пересылка активны)"
    override fun bmSmsOffRes(): String = "выключен ❌ (сотовая отправка отключена)"
    override fun rsFileNotFound(path: String): String = "⚠️ Файл не найден: $path"
    override fun rsPhotoNotFound(path: String): String = "⚠️ Фото не найдено: $path"
    override fun rsVideoNotFound(path: String): String = "⚠️ Видео не найдено: $path"
    override fun rsVoiceNotFound(path: String): String = "⚠️ Голосовое сообщение не найдено: $path"
    override fun rsAudioNotFound(path: String): String = "⚠️ Аудиофайл не найден: $path"
    override fun rsMediaGroupNotFound(): String = "⚠️ Файлы медиагруппы не найдены"
    override fun rsSendError(msg: String): String = "⚠️ Ошибка отправки файла в чат: $msg"
    override fun engCmdError(msg: String): String = "⚠️ Ошибка обработки команды: $msg"
    override fun engCbError(msg: String): String = "⚠️ <b>Ошибка обработки:</b> $msg"
    override fun engApkTooBigCloud(sizeMb: Long, apk: String): String = "⚠️ <b>Файл превышает лимит Telegram Cloud API ($sizeMb МБ > 20 МБ)</b>\n\nОблачный Telegram Bot API запрещает ботам скачивать файлы размером более 20 МБ через команду getFile.\n\n💡 <b>Как установить этот APK:</b>\n1. Загрузите файл на телефон вручную или через ADB:\n   <code>adb push \"$apk\" /sdcard/Download/</code>\n2. Отправьте боту команду установки:\n   <code>/install /sdcard/Download/$apk</code>\n\n<i>Либо подключите локальный Telegram Bot API Server (поддержка файлов до 2000 МБ).</i>"
    override fun engApkDownloading(apk: String): String = "⏳ Скачивание APK пакета <code>$apk</code>..."
    override fun engApkTooBigSimple(apk: String): String = "❌ <b>Файл превышает лимит Telegram Cloud API (20 МБ).</b>\nЗагрузите APK локально и выполните: <code>/install /sdcard/Download/$apk</code>"
    override fun engApkError(msg: String): String = "❌ Ошибка скачивания или установки APK: $msg"
    override fun engBackupError(msg: String): String = "❌ Ошибка загрузки файла бэкапа: $msg"
    override fun tfUnauthorizedPin(): String = "Ошибка: неверный SMS PIN или неавторизованный номер"
    override fun tfUnauthorizedNumber(): String = "Ошибка: неавторизованный номер отправителя"
    override fun tfErrorReply(msg: String): String = "Ошибка: $msg"
    override fun tfIncomingSmsHeader(from: String, sim: String, time: String): String = "📨 <b>Входящее SMS от $from</b>$sim (<i>$time</i>):\n"
    override fun tfReplyHint(): String = "<i>💬 Нажмите «Ответить» ниже или сделайте Reply на сообщение</i>"
    override fun tfBtnReplySms(): String = "💬 Ответить по SMS"
    override fun tfSmsForwardText(sender: String, body: String): String = "SMS от $sender: $body"
    override fun tfUnknownNumber(): String = "Неизвестный номер"
    override fun tfCallIncoming(): String = "📞 <b>Входящий звонок</b>"
    override fun tfCallMissed(): String = "🔴 <b>Пропущенный звонок</b>"
    override fun tfFrom(): String = "от"
    override fun tfMissedCallText(caller: String): String = "Пропущенный звонок от: $caller"
    override fun irEnterSmsText(): String = "Введите текст SMS в поле ввода"
    override fun irBtnCancel(): String = "❌ Отмена"
    override fun irSmsReplyPrompt(phone: String, sim: String): String = "✍️ <b>Введите текст сообщения для</b> <code>$phone</code>$sim:\n<i>(Просто напишите текст в чат или нажмите «Отмена»)</i>"
    override fun irCancelled(): String = "Отменено"
    override fun irSmsCancelled(): String = "❌ Отправка SMS отменена."
    override fun irEnterReplyText(): String = "Введите текст ответа"
    override fun irNotifReplyPrompt(id: String): String = "💬 <b>Ответ на уведомление #$id:</b>\n<i>(Напишите текст ответа в чат или нажмите «Отмена»)</i>"
    override fun ir2faDisabled(): String = "❌ Экспериментальный перехват 2FA выключен в настройках бота"
    override fun irActionDone(): String = "✅ Действие выполнено!"
    override fun irActionError(msg: String): String = "❌ Ошибка: $msg"
    override fun irActionFailed(): String = "Не удалось выполнить"
    override fun irActionStatusOk(time: String): String = "\n\n<i>✅ Действие выполнено в $time</i>"
    override fun irActionStatusErr(msg: String): String = "\n\n<i>❌ Ошибка действия: $msg</i>"
    override fun irTimeout(): String = "Таймаут"
    override fun irSettingsUpdated(): String = "Настройки обновлены"
    override fun irSmsSent(phone: String, sim: String, text: String): String = "✅ <b>SMS успешно отправлено</b> на номер <code>$phone</code>$sim:\n«$text»"
    override fun irSmsSendFailed(): String = "Не удалось отправить SMS"
    override fun irSmsSendError(phone: String, err: String): String = "❌ <b>Ошибка отправки SMS</b> на номер <code>$phone</code>:\n$err"
    override fun irNotifReplyCancelled(): String = "❌ Ответ на уведомление отменен."
    override fun irReplyToAppSent(text: String): String = "✅ <b>Ответ успешно отправлен в приложение:</b>\n«$text»"
    override fun irReplySendFailed(): String = "Не удалось отправить ответ"
    override fun irNotifReplyError(id: String, err: String): String = "❌ <b>Ошибка ответа на уведомление #$id:</b>\n$err"
    override fun irQuickReplySent(text: String): String = "✅ <b>Ответ отправлен в приложение:</b>\n«$text»"
    override fun irQuickReplyFailed(): String = "Не удалось ответить"
    override fun irQuickReplyError(err: String): String = "❌ <b>Ошибка ответа на уведомление:</b> $err"
    override fun efBtnReply(): String = "💬 Ответить"
    override fun efShakeAlert(): String = "🚨 <b>ВНИМАНИЕ: Сработал датчик тряски!</b>\nУстройство подверглось резкому ускорению/встряхиванию."
    override fun efSoundAlert(db: Int, threshold: Int, level: Int): String = "🚨 <b>ВНИМАНИЕ: Сработал детектор звука!</b>\nЗафиксирован шум громкостью <b>$db dB</b> (порог: <b>$threshold dB</b>, уровень $level/10)."
    override fun efMotionAlert(): String = "🚨 <b>ВНИМАНИЕ: Детектор движения зафиксировал активность!</b>"
    override fun efChargingOn(): String = "<b>Подключено к зарядному устройству</b>"
    override fun efChargingOff(): String = "<b>Отключено от зарядного устройства</b>"
    override fun efLevel(level: Int): String = "• Уровень заряда: <b>$level%</b>"
    override fun efSource(plug: String): String = "• Источник: <b>$plug</b>"
    override fun efVoltage(mv: Int): String = "• Напряжение: $mv mV"
    override fun efOnBattery(): String = "• Режим: <b>Работа от аккумулятора (разрядка)</b>"
    override fun efTemperature(temp: String): String = "• Температура: ${temp}°C"
    override fun efLowAlert(level: Int): String = "🪫 <b>ВНИМАНИЕ: Низкий заряд аккумулятора!</b>\n\n• Текущий уровень: <b>$level%</b>\n• Статус: <b>Разряжается</b>\n• Рекомендация: <i>Подключите устройство к зарядному устройству!</i>"
    override fun permName(id: String): String =
        when (id) {
            "root" -> "Root (Superuser)"
            "install_packages" -> "Установка APK"
            "manage_storage" -> "Все файлы (Storage)"
            "accessibility" -> "Спец. возможности"
            "write_settings" -> "Настройки системы"
            "overlay" -> "Поверх других окон"
            "notification_listener" -> "Чтение уведомлений"
            "sms" -> "SMS (Отправка/Чтение)"
            "calls" -> "Звонки и журнал"
            "dnd" -> "Режим «Не беспокоить»"
            "camera" -> "Камера"
            "microphone" -> "Микрофон (Запись звука)"
            "location" -> "Геолокация (GPS)"
            "contacts" -> "Контакты"
            "battery_opt" -> "Doze Mode (Без ограничений)"
            "post_notifications" -> "Уведомления в шторку"
            "media_photos" -> "Фото и галерея"
            "media_audio" -> "Аудиофайлы"
            "bluetooth" -> "Bluetooth адаптер"
            "data_saver" -> "Фоновый трафик"
            else -> id
        }

    override fun permDesc(id: String): String =
        when (id) {
            "root" -> "Полный доступ суперпользователя (libsu)"
            "install_packages" -> "REQUEST_INSTALL_PACKAGES для обновления приложения"
            "manage_storage" -> "MANAGE_EXTERNAL_STORAGE доступ к диску"
            "accessibility" -> "Accessibility Service автоматизация кликов/USSD"
            "write_settings" -> "WRITE_SETTINGS управление автояркостью"
            "overlay" -> "SYSTEM_ALERT_WINDOW системные диалоги"
            "notification_listener" -> "Перехват входящих push-уведомлений"
            "sms" -> "SEND_SMS, RECEIVE_SMS управление через SMS"
            "calls" -> "CALL_PHONE, READ_CALL_LOG, READ_PHONE_STATE"
            "dnd" -> "ACCESS_NOTIFICATION_POLICY профили звука"
            "camera" -> "CAMERA съемка фото и видео"
            "microphone" -> "RECORD_AUDIO скрытая аудиозапись"
            "location" -> "ACCESS_FINE_LOCATION координаты и трекинг"
            "contacts" -> "READ_CONTACTS определение имен в вызовах/SMS"
            "battery_opt" -> "Игнорирование оптимизации батареи 24/7"
            "post_notifications" -> "POST_NOTIFICATIONS фоновая служба FGS"
            "media_photos" -> "Чтение фото/видео из MediaStore"
            "media_audio" -> "Чтение аудиофайлов для плеера"
            "bluetooth" -> "BLUETOOTH_CONNECT управление Bluetooth"
            "data_saver" -> "Работа в режиме экономии трафика"
            else -> ""
        }
    override fun efPlugLabel(plug: String): String =
        when (plug) {
            "AC (220V)" -> "Сеть 220V (AC)"
            "USB port" -> "USB порт"
            "Wireless charging" -> "Беспроводная зарядка"
            "Charger" -> "Зарядное устройство"
            "Not connected" -> "Не подключено"
            else -> plug
        }
}

object TelegramBotStrings {
    fun get(language: BotLanguage): BotStrings {
        return when (language) {
            BotLanguage.EN -> BotStringsEn
            BotLanguage.RU -> BotStringsRu
            BotLanguage.AUTO -> {
                val sysLang = java.util.Locale.getDefault().language
                if (sysLang.equals("ru", ignoreCase = true)) BotStringsRu else BotStringsEn
            }
        }
    }
}
