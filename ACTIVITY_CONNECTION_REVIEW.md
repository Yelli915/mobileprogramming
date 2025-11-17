# Activity 연결 상태 검토 결과

## 현재 Activity 목록

1. **MainActivity** (LAUNCHER)
2. **LoginActivity**
3. **SettingsActivity**
4. **MapActivity** ⚠️
5. **RunningStartActivity**
6. **RunningRecordActivity**
7. **SketchRunActivity**

---

## 연결 상태 분석

### ✅ 정상 연결

#### 1. MainActivity
- ✅ → SettingsActivity (설정 버튼)
- ✅ → LoginActivity (미로그인 시 자동)
- ✅ → RunningStartActivity (일반 운동 시작)
- ✅ → SketchRunActivity (코스 선택하기)

#### 2. LoginActivity
- ✅ → MainActivity (로그인 성공 후)

#### 3. SettingsActivity
- ✅ → LoginActivity (로그아웃)
- ✅ → MainActivity (뒤로가기)

#### 4. SketchRunActivity
- ✅ → RunningStartActivity (코스 선택 후 시작)
- ✅ → MainActivity (뒤로가기)

#### 5. RunningStartActivity
- ✅ → RunningRecordActivity (운동 종료)
- ✅ → MainActivity (뒤로가기)

#### 6. RunningRecordActivity
- ✅ → MainActivity (뒤로가기)

---

### ⚠️ 문제점

#### 1. MapActivity - 연결 없음
- **상태**: AndroidManifest에 등록되어 있으나 어디서도 사용되지 않음
- **문제**: MainActivity에서 MapActivity로 가는 연결이 제거됨
- **해결 방안**:
  - 옵션 1: MapActivity 제거 (RunningStartActivity가 더 완전한 기능 제공)
  - 옵션 2: MapActivity를 다른 용도로 활용 (예: 지도만 보는 화면)
  - 옵션 3: MainActivity에서 MapActivity로 연결 복구

#### 2. SketchRunActivity - 미구현 기능
- `btnFindLocation`: Toast만 표시, 실제 기능 없음
- `btnCourseDetail`: Toast만 표시, 상세 화면 없음

#### 3. RunningRecordActivity - 미구현 기능
- `rateButton`: 구현 없음
- `shareButton`: 구현 없음
- `viewMoreButton`: 구현 없음
- `statisticsButton`: 상태만 변경, 실제 통계 화면 없음

---

## 권장 수정 사항

### 1. MapActivity 처리
MapActivity는 현재 사용되지 않으므로:
- **권장**: AndroidManifest에서 제거하거나
- **대안**: MainActivity에서 간단한 지도 보기 기능으로 연결

### 2. 누락된 연결 추가
필요한 경우 추가 연결:
- RunningRecordActivity → MainActivity (명시적 연결, 현재는 finish()만)
- SketchRunActivity → MapActivity (위치 찾기 기능)

---

## 현재 화면 전환 흐름도

```
앱 시작
  ↓
MainActivity (LAUNCHER)
  ├─→ SettingsActivity → LoginActivity (로그아웃)
  ├─→ RunningStartActivity → RunningRecordActivity
  └─→ SketchRunActivity → RunningStartActivity → RunningRecordActivity
```

**MapActivity는 현재 흐름에 포함되지 않음**

