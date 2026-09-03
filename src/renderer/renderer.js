(() => {
  const periodLabel = document.getElementById('periodLabel');
  const monthGrid = document.getElementById('monthGrid');
  const timeGrid = document.getElementById('timeGrid');
  const weekdaysRow = document.getElementById('weekdaysRow');
  const prevBtn = document.getElementById('prevBtn');
  const nextBtn = document.getElementById('nextBtn');
  const todayBtn = document.getElementById('todayBtn');
  const viewSwitch = document.getElementById('viewSwitch');
  const themeToggleBtn = document.getElementById('themeToggleBtn');
  const addEventBtn = document.getElementById('addEventBtn');
  const addToDayBtn = document.getElementById('addToDayBtn');
  const selectedDateLabel = document.getElementById('selectedDateLabel');
  const eventList = document.getElementById('eventList');
  const categoryFilter = document.getElementById('categoryFilter');
  const manageCategoriesBtn = document.getElementById('manageCategoriesBtn');

  const modalOverlay = document.getElementById('modalOverlay');
  const modalTitle = document.getElementById('modalTitle');
  const eventForm = document.getElementById('eventForm');
  const fieldTitle = document.getElementById('fieldTitle');
  const fieldDate = document.getElementById('fieldDate');
  const fieldStart = document.getElementById('fieldStart');
  const fieldEnd = document.getElementById('fieldEnd');
  const fieldCategory = document.getElementById('fieldCategory');
  const fieldFreq = document.getElementById('fieldFreq');
  const recurrenceDetail = document.getElementById('recurrenceDetail');
  const fieldInterval = document.getElementById('fieldInterval');
  const intervalUnit = document.getElementById('intervalUnit');
  const fieldUntil = document.getElementById('fieldUntil');
  const fieldReminder = document.getElementById('fieldReminder');
  const fieldMemo = document.getElementById('fieldMemo');
  const fieldId = document.getElementById('fieldId');
  const cancelBtn = document.getElementById('cancelBtn');
  const deleteBtn = document.getElementById('deleteBtn');

  const categoryModalOverlay = document.getElementById('categoryModalOverlay');
  const categoryManageList = document.getElementById('categoryManageList');
  const newCategoryColor = document.getElementById('newCategoryColor');
  const newCategoryName = document.getElementById('newCategoryName');
  const addCategoryBtn = document.getElementById('addCategoryBtn');
  const closeCategoryModalBtn = document.getElementById('closeCategoryModalBtn');

  const WEEKDAY_LABELS = ['일', '월', '화', '수', '목', '금', '토'];
  const HOUR_HEIGHT = 48;

  let events = [];
  let categories = [];
  let activeCategoryIds = new Set();
  let viewMode = 'month';
  let cursorDate = new Date();
  let selectedDate = toKey(new Date());
  const firedReminders = new Set();

  // ---------- date helpers ----------
  function toKey(date) {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
  }

  function parseKey(key) {
    const [y, m, d] = key.split('-').map(Number);
    return new Date(y, m - 1, d);
  }

  function addDays(date, days) {
    const d = new Date(date);
    d.setDate(d.getDate() + days);
    return d;
  }

  function addMonthsClamped(anchor, months) {
    const y = anchor.getFullYear();
    const m = anchor.getMonth() + months;
    const targetYear = y + Math.floor(m / 12);
    const targetMonth = ((m % 12) + 12) % 12;
    const day = anchor.getDate();
    const lastDay = new Date(targetYear, targetMonth + 1, 0).getDate();
    return new Date(targetYear, targetMonth, Math.min(day, lastDay));
  }

  function addYearsClamped(anchor, years) {
    const targetYear = anchor.getFullYear() + years;
    const month = anchor.getMonth();
    const day = anchor.getDate();
    const lastDay = new Date(targetYear, month + 1, 0).getDate();
    return new Date(targetYear, month, Math.min(day, lastDay));
  }

  function startOfWeek(date) {
    const d = new Date(date);
    d.setDate(d.getDate() - d.getDay());
    d.setHours(0, 0, 0, 0);
    return d;
  }

  function timeToMinutes(t) {
    if (!t) return null;
    const [h, m] = t.split(':').map(Number);
    return h * 60 + m;
  }

  function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
  }

  // ---------- init ----------
  async function init() {
    applyStoredTheme();
    categories = await window.api.loadCategories();
    events = await window.api.loadEvents();
    activeCategoryIds = new Set(categories.map((c) => c.id));
    populateCategorySelect();
    render();
    startNowLineTimer();
    startReminderScheduler();
  }

  function applyStoredTheme() {
    let saved = 'dark';
    try {
      saved = localStorage.getItem('theme') || 'dark';
    } catch (err) {
      saved = 'dark';
    }
    document.documentElement.setAttribute('data-theme', saved);
    themeToggleBtn.textContent = saved === 'dark' ? '🌙' : '☀️';
  }

  themeToggleBtn.addEventListener('click', () => {
    const current = document.documentElement.getAttribute('data-theme') || 'dark';
    const next = current === 'dark' ? 'light' : 'dark';
    document.documentElement.setAttribute('data-theme', next);
    themeToggleBtn.textContent = next === 'dark' ? '🌙' : '☀️';
    try {
      localStorage.setItem('theme', next);
    } catch (err) {
      /* ignore storage failures */
    }
  });

  // ---------- view switch ----------
  viewSwitch.addEventListener('click', (ev) => {
    const btn = ev.target.closest('.view-btn');
    if (!btn) return;
    setViewMode(btn.dataset.view);
  });

  function setViewMode(mode) {
    viewMode = mode;
    [...viewSwitch.children].forEach((b) => b.classList.toggle('active', b.dataset.view === mode));
    render();
  }

  // ---------- categories ----------
  function categoryById(id) {
    return categories.find((c) => c.id === id) || { id: 'unknown', name: '기타', color: '#9a9ca6' };
  }

  function populateCategorySelect() {
    fieldCategory.innerHTML = '';
    categories.forEach((c) => {
      const opt = document.createElement('option');
      opt.value = c.id;
      opt.textContent = c.name;
      fieldCategory.appendChild(opt);
    });
  }

  function renderCategoryFilter() {
    categoryFilter.innerHTML = '';
    categories.forEach((c) => {
      const li = document.createElement('li');

      const checkbox = document.createElement('input');
      checkbox.type = 'checkbox';
      checkbox.checked = activeCategoryIds.has(c.id);
      checkbox.addEventListener('change', () => {
        if (checkbox.checked) activeCategoryIds.add(c.id);
        else activeCategoryIds.delete(c.id);
        render();
      });

      const dot = document.createElement('span');
      dot.className = 'category-dot';
      dot.style.background = c.color;

      const label = document.createElement('span');
      label.textContent = c.name;

      li.appendChild(checkbox);
      li.appendChild(dot);
      li.appendChild(label);
      li.addEventListener('click', (ev) => {
        if (ev.target === checkbox) return;
        checkbox.checked = !checkbox.checked;
        checkbox.dispatchEvent(new Event('change'));
      });
      categoryFilter.appendChild(li);
    });
  }

  manageCategoriesBtn.addEventListener('click', () => {
    renderCategoryManageList();
    categoryModalOverlay.classList.remove('hidden');
  });
  closeCategoryModalBtn.addEventListener('click', () => {
    categoryModalOverlay.classList.add('hidden');
  });
  categoryModalOverlay.addEventListener('click', (ev) => {
    if (ev.target === categoryModalOverlay) categoryModalOverlay.classList.add('hidden');
  });

  function renderCategoryManageList() {
    categoryManageList.innerHTML = '';
    categories.forEach((c) => {
      const li = document.createElement('li');
      li.className = 'category-manage-item';

      const colorInput = document.createElement('input');
      colorInput.type = 'color';
      colorInput.value = c.color;
      colorInput.addEventListener('input', async () => {
        c.color = colorInput.value;
        await persistCategories();
        render();
      });

      const nameInput = document.createElement('input');
      nameInput.type = 'text';
      nameInput.value = c.name;
      nameInput.maxLength = 30;
      nameInput.addEventListener('change', async () => {
        const trimmed = nameInput.value.trim();
        if (!trimmed) {
          nameInput.value = c.name;
          return;
        }
        c.name = trimmed;
        await persistCategories();
        populateCategorySelect();
        render();
      });

      const delBtn = document.createElement('button');
      delBtn.type = 'button';
      delBtn.textContent = '✕';
      delBtn.title = '삭제';
      delBtn.addEventListener('click', async () => {
        if (categories.length <= 1) {
          alert('최소 1개의 카테고리가 필요합니다.');
          return;
        }
        if (!confirm(`"${c.name}" 카테고리를 삭제할까요? 이 카테고리의 일정은 다른 카테고리로 이동됩니다.`)) return;
        const fallback = categories.find((x) => x.id !== c.id);
        events.forEach((e) => {
          if (e.categoryId === c.id) e.categoryId = fallback.id;
        });
        categories = categories.filter((x) => x.id !== c.id);
        activeCategoryIds.delete(c.id);
        await persistCategories();
        await persistEvents();
        populateCategorySelect();
        renderCategoryManageList();
        render();
      });

      li.appendChild(colorInput);
      li.appendChild(nameInput);
      li.appendChild(delBtn);
      categoryManageList.appendChild(li);
    });
  }

  addCategoryBtn.addEventListener('click', async () => {
    const name = newCategoryName.value.trim();
    if (!name) return;
    const id = 'cat_' + Date.now().toString(36) + Math.random().toString(36).slice(2, 6);
    categories.push({ id, name, color: newCategoryColor.value });
    activeCategoryIds.add(id);
    await persistCategories();
    newCategoryName.value = '';
    populateCategorySelect();
    renderCategoryManageList();
    render();
  });

  async function persistCategories() {
    await window.api.saveCategories(categories);
  }
  async function persistEvents() {
    await window.api.saveEvents(events);
  }

  // ---------- recurrence expansion ----------
  function expandOccurrences(event, rangeStartKey, rangeEndKey) {
    const results = [];
    const rec = event.recurrence;
    if (!rec || !rec.freq || rec.freq === 'none') {
      if (event.date >= rangeStartKey && event.date <= rangeEndKey) {
        results.push({ ...event, occurrenceDate: event.date });
      }
      return results;
    }

    const anchor = parseKey(event.date);
    const rangeStart = parseKey(rangeStartKey);
    const rangeEnd = parseKey(rangeEndKey);
    if (anchor > rangeEnd) return results;

    const interval = Math.max(1, parseInt(rec.interval, 10) || 1);
    const until = rec.until ? parseKey(rec.until) : null;
    if (until && until < anchor) return results;
    if (until && until < rangeStart) return results;

    const cap = addYearsClamped(rangeEnd, 3);
    const hardEnd = until && until < cap ? until : cap;

    const MAX_OCC = 3000;
    let step = 0;
    while (step < MAX_OCC) {
      let occ;
      if (rec.freq === 'daily') occ = addDays(anchor, step * interval);
      else if (rec.freq === 'weekly') occ = addDays(anchor, step * interval * 7);
      else if (rec.freq === 'monthly') occ = addMonthsClamped(anchor, step * interval);
      else if (rec.freq === 'yearly') occ = addYearsClamped(anchor, step * interval);
      else break;

      if (occ > hardEnd) break;
      if (occ >= rangeStart && occ <= rangeEnd) {
        results.push({ ...event, occurrenceDate: toKey(occ) });
      }
      step++;
    }
    return results;
  }

  function getOccurrencesInRange(rangeStartKey, rangeEndKey) {
    const list = [];
    events.forEach((e) => {
      if (!activeCategoryIds.has(e.categoryId)) return;
      expandOccurrences(e, rangeStartKey, rangeEndKey).forEach((occ) => list.push(occ));
    });
    return list;
  }

  // ---------- render dispatch ----------
  function render() {
    renderCategoryFilter();
    renderPeriodLabel();
    if (viewMode === 'month') {
      weekdaysRow.classList.remove('hidden');
      monthGrid.classList.remove('hidden');
      timeGrid.classList.add('hidden');
      renderMonthGrid();
    } else {
      weekdaysRow.classList.add('hidden');
      monthGrid.classList.add('hidden');
      timeGrid.classList.remove('hidden');
      renderTimeGrid();
    }
    renderSidebarEvents();
  }

  function renderPeriodLabel() {
    if (viewMode === 'month') {
      periodLabel.textContent = `${cursorDate.getFullYear()}년 ${cursorDate.getMonth() + 1}월`;
    } else if (viewMode === 'week') {
      const start = startOfWeek(cursorDate);
      const end = addDays(start, 6);
      if (start.getMonth() === end.getMonth()) {
        periodLabel.textContent = `${start.getFullYear()}년 ${start.getMonth() + 1}월 ${start.getDate()}~${end.getDate()}일`;
      } else {
        periodLabel.textContent = `${start.getMonth() + 1}.${start.getDate()} ~ ${end.getMonth() + 1}.${end.getDate()}`;
      }
    } else {
      periodLabel.textContent = `${cursorDate.getFullYear()}년 ${cursorDate.getMonth() + 1}월 ${cursorDate.getDate()}일`;
    }
  }

  // ---------- month view ----------
  function renderMonthGrid() {
    monthGrid.innerHTML = '';
    const viewYear = cursorDate.getFullYear();
    const viewMonth = cursorDate.getMonth();
    const firstOfMonth = new Date(viewYear, viewMonth, 1);
    const startWeekday = firstOfMonth.getDay();
    const gridStart = addDays(firstOfMonth, -startWeekday);
    const gridEnd = addDays(gridStart, 41);
    const occurrences = getOccurrencesInRange(toKey(gridStart), toKey(gridEnd));
    const byDate = {};
    occurrences.forEach((o) => {
      (byDate[o.occurrenceDate] = byDate[o.occurrenceDate] || []).push(o);
    });
    Object.values(byDate).forEach((list) =>
      list.sort((a, b) => (a.start || '99:99').localeCompare(b.start || '99:99'))
    );

    const todayKey = toKey(new Date());

    for (let i = 0; i < 42; i++) {
      const cellDate = addDays(gridStart, i);
      const key = toKey(cellDate);
      const cell = document.createElement('div');
      cell.className = 'day-cell';
      if (cellDate.getMonth() !== viewMonth) cell.classList.add('other-month');
      if (key === todayKey) cell.classList.add('today');
      if (key === selectedDate) cell.classList.add('selected');

      const num = document.createElement('div');
      num.className = 'day-number';
      num.textContent = cellDate.getDate();
      cell.appendChild(num);

      const dayEventsEl = document.createElement('div');
      dayEventsEl.className = 'day-events';
      const dayItems = byDate[key] || [];
      const maxShown = 3;
      dayItems.slice(0, maxShown).forEach((e) => {
        const chip = document.createElement('div');
        chip.className = 'event-chip';
        chip.style.background = categoryById(e.categoryId).color;
        chip.textContent = e.start ? `${e.start} ${e.title}` : e.title;
        dayEventsEl.appendChild(chip);
      });
      if (dayItems.length > maxShown) {
        const more = document.createElement('div');
        more.className = 'more-chip';
        more.textContent = `+${dayItems.length - maxShown}건 더보기`;
        dayEventsEl.appendChild(more);
      }
      cell.appendChild(dayEventsEl);

      cell.addEventListener('click', () => {
        selectedDate = key;
        cursorDate = cellDate;
        render();
      });
      cell.addEventListener('dblclick', () => openModal({ date: key }));

      monthGrid.appendChild(cell);
    }
  }

  // ---------- week / day view ----------
  function weekDays(date) {
    const start = startOfWeek(date);
    return Array.from({ length: 7 }, (_, i) => addDays(start, i));
  }

  function spacer() {
    const s = document.createElement('div');
    s.className = 'time-gutter-spacer';
    return s;
  }

  function layoutOverlaps(sortedItems) {
    const columns = [];
    sortedItems.forEach((ev) => {
      ev._startMin = timeToMinutes(ev.start);
      ev._endMin = ev.end ? timeToMinutes(ev.end) : ev._startMin + 30;
      if (ev._endMin <= ev._startMin) ev._endMin = ev._startMin + 30;
      let placed = false;
      for (let i = 0; i < columns.length; i++) {
        const col = columns[i];
        if (col[col.length - 1]._endMin <= ev._startMin) {
          col.push(ev);
          ev._col = i;
          placed = true;
          break;
        }
      }
      if (!placed) {
        ev._col = columns.length;
        columns.push([ev]);
      }
    });
    const totalCols = Math.max(1, columns.length);
    sortedItems.forEach((ev) => {
      ev._totalCols = totalCols;
    });
    return sortedItems;
  }

  function renderTimeGrid() {
    const prevScrollEl = timeGrid.querySelector('.time-scroll-area');
    const savedScrollTop = prevScrollEl ? prevScrollEl.scrollTop : null;

    timeGrid.innerHTML = '';
    const days = viewMode === 'day' ? [new Date(cursorDate)] : weekDays(cursorDate);
    const rangeStartKey = toKey(days[0]);
    const rangeEndKey = toKey(days[days.length - 1]);
    const occurrences = getOccurrencesInRange(rangeStartKey, rangeEndKey);
    const byDate = {};
    occurrences.forEach((o) => {
      (byDate[o.occurrenceDate] = byDate[o.occurrenceDate] || []).push(o);
    });

    const todayKey = toKey(new Date());

    const headerRow = document.createElement('div');
    headerRow.className = 'time-grid-header-row';
    headerRow.appendChild(spacer());
    days.forEach((d) => {
      const key = toKey(d);
      const h = document.createElement('div');
      h.className = 'time-day-header' + (key === todayKey ? ' today' : '');
      h.innerHTML = `<div>${WEEKDAY_LABELS[d.getDay()]}</div><div class="dnum">${d.getDate()}</div>`;
      h.addEventListener('click', () => {
        selectedDate = key;
        cursorDate = new Date(d);
        setViewMode('day');
      });
      headerRow.appendChild(h);
    });
    timeGrid.appendChild(headerRow);

    const alldayRow = document.createElement('div');
    alldayRow.className = 'time-allday-row';
    alldayRow.appendChild(spacer());
    days.forEach((d) => {
      const key = toKey(d);
      const col = document.createElement('div');
      col.className = 'allday-col';
      (byDate[key] || [])
        .filter((e) => !e.start)
        .forEach((e) => {
          const chip = document.createElement('div');
          chip.className = 'event-chip';
          chip.style.background = categoryById(e.categoryId).color;
          chip.textContent = e.title;
          chip.addEventListener('click', () => openModal({ ...e, date: e.occurrenceDate }));
          col.appendChild(chip);
        });
      alldayRow.appendChild(col);
    });
    timeGrid.appendChild(alldayRow);

    const scrollArea = document.createElement('div');
    scrollArea.className = 'time-scroll-area';

    const gutter = document.createElement('div');
    gutter.className = 'time-gutter';
    for (let h = 0; h < 24; h++) {
      const cell = document.createElement('div');
      cell.className = 'time-gutter-cell';
      cell.textContent = `${String(h).padStart(2, '0')}:00`;
      gutter.appendChild(cell);
    }
    scrollArea.appendChild(gutter);

    const daysWrap = document.createElement('div');
    daysWrap.className = 'time-days-wrap';

    days.forEach((d) => {
      const key = toKey(d);
      const col = document.createElement('div');
      col.className = 'time-day-col' + (key === todayKey ? ' today-col' : '');

      for (let h = 0; h < 24; h++) {
        const slot = document.createElement('div');
        slot.className = 'hour-slot';
        col.appendChild(slot);
      }

      col.addEventListener('dblclick', (ev) => {
        if (ev.target.closest('.time-event')) return;
        const rect = col.getBoundingClientRect();
        const offsetY = ev.clientY - rect.top;
        const rawMinutes = (offsetY / HOUR_HEIGHT) * 60;
        const minutes = Math.max(0, Math.min(1425, Math.round(rawMinutes / 15) * 15));
        const hh = String(Math.floor(minutes / 60)).padStart(2, '0');
        const mm = String(minutes % 60).padStart(2, '0');
        openModal({ date: key, start: `${hh}:${mm}` });
      });

      col.addEventListener('click', (ev) => {
        if (ev.target.closest('.time-event')) return;
        selectedDate = key;
        cursorDate = new Date(d);
        render();
      });

      const timedItems = (byDate[key] || [])
        .filter((e) => e.start)
        .sort((a, b) => a.start.localeCompare(b.start));
      const laid = layoutOverlaps(timedItems);
      laid.forEach((e) => {
        const top = (e._startMin / 60) * HOUR_HEIGHT;
        const height = Math.max(20, ((e._endMin - e._startMin) / 60) * HOUR_HEIGHT - 2);
        const block = document.createElement('div');
        block.className = 'time-event';
        block.style.top = `${top}px`;
        block.style.height = `${height}px`;
        block.style.left = `${(e._col / e._totalCols) * 100}%`;
        block.style.width = `${100 / e._totalCols - 2}%`;
        block.style.background = categoryById(e.categoryId).color;
        block.innerHTML = `<div class="te-title">${escapeHtml(e.title)}</div><div class="te-time">${e.start}${
          e.end ? ' - ' + e.end : ''
        }</div>`;
        block.addEventListener('click', (ev) => {
          ev.stopPropagation();
          openModal({ ...e, date: e.occurrenceDate });
        });
        col.appendChild(block);
      });

      if (key === todayKey) {
        const now = new Date();
        const nowMin = now.getHours() * 60 + now.getMinutes();
        const line = document.createElement('div');
        line.className = 'now-line';
        line.style.top = `${(nowMin / 60) * HOUR_HEIGHT}px`;
        col.appendChild(line);
      }

      daysWrap.appendChild(col);
    });

    scrollArea.appendChild(daysWrap);
    timeGrid.appendChild(scrollArea);

    requestAnimationFrame(() => {
      scrollArea.scrollTop = savedScrollTop !== null ? savedScrollTop : 7 * HOUR_HEIGHT;
    });
  }

  // ---------- sidebar ----------
  function renderSidebarEvents() {
    const date = parseKey(selectedDate);
    const weekday = WEEKDAY_LABELS[date.getDay()];
    selectedDateLabel.textContent = `${date.getFullYear()}년 ${date.getMonth() + 1}월 ${date.getDate()}일 (${weekday})`;

    eventList.innerHTML = '';
    const dayItems = getOccurrencesInRange(selectedDate, selectedDate).sort((a, b) =>
      (a.start || '99:99').localeCompare(b.start || '99:99')
    );

    if (dayItems.length === 0) {
      const empty = document.createElement('li');
      empty.className = 'empty-state';
      empty.textContent = '이 날의 일정이 없습니다.';
      eventList.appendChild(empty);
      return;
    }

    dayItems.forEach((e) => {
      const cat = categoryById(e.categoryId);
      const li = document.createElement('li');
      li.className = 'event-item';
      li.style.borderLeftColor = cat.color;

      const title = document.createElement('div');
      title.className = 'event-title';
      title.textContent = e.title;
      if (e.recurrence && e.recurrence.freq !== 'none') {
        const badge = document.createElement('span');
        badge.className = 'badge';
        badge.textContent = '반복';
        title.appendChild(badge);
      }
      li.appendChild(title);

      if (e.start || e.end) {
        const time = document.createElement('div');
        time.className = 'event-time';
        time.textContent = [e.start, e.end].filter(Boolean).join(' - ');
        li.appendChild(time);
      }

      if (e.memo) {
        const memo = document.createElement('div');
        memo.className = 'event-memo';
        memo.textContent = e.memo;
        li.appendChild(memo);
      }

      li.addEventListener('click', () => openModal({ ...e, date: e.occurrenceDate }));
      eventList.appendChild(li);
    });
  }

  // ---------- modal ----------
  function updateRecurrenceUI() {
    const freq = fieldFreq.value;
    if (freq === 'none') {
      recurrenceDetail.classList.add('hidden');
      return;
    }
    recurrenceDetail.classList.remove('hidden');
    const unitMap = { daily: '일마다', weekly: '주마다', monthly: '개월마다', yearly: '년마다' };
    intervalUnit.textContent = unitMap[freq] || '';
  }
  fieldFreq.addEventListener('change', updateRecurrenceUI);

  function updateReminderAvailability() {
    if (!fieldStart.value) {
      fieldReminder.value = '';
      fieldReminder.disabled = true;
    } else {
      fieldReminder.disabled = false;
    }
  }
  fieldStart.addEventListener('input', updateReminderAvailability);

  function openModal(data) {
    const isEdit = Boolean(data.id);
    modalTitle.textContent = isEdit ? '일정 수정' : '새 일정';
    fieldId.value = data.id || '';
    fieldTitle.value = data.title || '';
    fieldDate.value = data.date || selectedDate;
    fieldStart.value = data.start || '';
    fieldEnd.value = data.end || '';
    fieldMemo.value = data.memo || '';
    fieldCategory.value = data.categoryId || (categories[0] && categories[0].id) || '';

    const rec = data.recurrence || { freq: 'none', interval: 1, until: '' };
    fieldFreq.value = rec.freq || 'none';
    fieldInterval.value = rec.interval || 1;
    fieldUntil.value = rec.until || '';
    updateRecurrenceUI();

    fieldReminder.value =
      data.reminderMinutes === null || data.reminderMinutes === undefined ? '' : String(data.reminderMinutes);
    updateReminderAvailability();

    deleteBtn.classList.toggle('hidden', !isEdit);
    modalOverlay.classList.remove('hidden');
    fieldTitle.focus();
  }

  function closeModal() {
    modalOverlay.classList.add('hidden');
    eventForm.reset();
  }

  eventForm.addEventListener('submit', async (ev) => {
    ev.preventDefault();
    const id = fieldId.value;
    const start = fieldStart.value;
    const end = fieldEnd.value;

    if (start && end && end <= start) {
      alert('종료 시간은 시작 시간보다 이후여야 합니다.');
      return;
    }

    const freq = fieldFreq.value;
    const reminderRaw = fieldReminder.value;
    if (reminderRaw !== '' && !start) {
      alert('알림을 설정하려면 시작 시간을 입력해야 합니다.');
      return;
    }

    const payload = {
      title: fieldTitle.value.trim(),
      date: fieldDate.value,
      start,
      end,
      categoryId: fieldCategory.value,
      memo: fieldMemo.value.trim(),
      recurrence:
        freq === 'none'
          ? { freq: 'none' }
          : {
              freq,
              interval: Math.max(1, parseInt(fieldInterval.value, 10) || 1),
              until: fieldUntil.value || null,
            },
      reminderMinutes: reminderRaw === '' ? null : parseInt(reminderRaw, 10),
    };

    if (!payload.title || !payload.date) return;

    if (id) {
      const idx = events.findIndex((e) => e.id === id);
      if (idx !== -1) events[idx] = { ...events[idx], ...payload };
    } else {
      events.push({ id: crypto.randomUUID(), ...payload });
    }

    selectedDate = payload.date;
    cursorDate = parseKey(payload.date);

    await persistEvents();
    closeModal();
    render();
  });

  deleteBtn.addEventListener('click', async () => {
    const id = fieldId.value;
    if (!id) return;
    if (!confirm('이 일정을 삭제하시겠습니까? 반복 일정이면 전체가 삭제됩니다.')) return;
    events = events.filter((e) => e.id !== id);
    await persistEvents();
    closeModal();
    render();
  });

  cancelBtn.addEventListener('click', closeModal);
  modalOverlay.addEventListener('click', (ev) => {
    if (ev.target === modalOverlay) closeModal();
  });
  document.addEventListener('keydown', (ev) => {
    if (ev.key === 'Escape') {
      if (!modalOverlay.classList.contains('hidden')) closeModal();
      if (!categoryModalOverlay.classList.contains('hidden')) categoryModalOverlay.classList.add('hidden');
    }
  });

  // ---------- navigation ----------
  function navigate(dir) {
    if (viewMode === 'month') {
      cursorDate = new Date(cursorDate.getFullYear(), cursorDate.getMonth() + dir, 1);
    } else if (viewMode === 'week') {
      cursorDate = addDays(cursorDate, dir * 7);
    } else {
      cursorDate = addDays(cursorDate, dir);
    }
    render();
  }
  prevBtn.addEventListener('click', () => navigate(-1));
  nextBtn.addEventListener('click', () => navigate(1));

  todayBtn.addEventListener('click', () => {
    cursorDate = new Date();
    selectedDate = toKey(cursorDate);
    render();
  });

  addEventBtn.addEventListener('click', () => openModal({ date: selectedDate }));
  addToDayBtn.addEventListener('click', () => openModal({ date: selectedDate }));

  // ---------- now line refresh ----------
  function startNowLineTimer() {
    setInterval(() => {
      if (viewMode !== 'month') renderTimeGrid();
    }, 60000);
  }

  // ---------- reminders ----------
  function startReminderScheduler() {
    checkReminders();
    setInterval(checkReminders, 20000);
  }

  function checkReminders() {
    const today = new Date();
    const rangeStartKey = toKey(today);
    const rangeEndKey = toKey(addDays(today, 2));
    const occurrences = [];
    events.forEach((e) => {
      if (e.reminderMinutes === null || e.reminderMinutes === undefined || !e.start) return;
      expandOccurrences(e, rangeStartKey, rangeEndKey).forEach((o) => occurrences.push(o));
    });

    const now = Date.now();
    occurrences.forEach((o) => {
      const key = `${o.id}@${o.occurrenceDate}`;
      if (firedReminders.has(key)) return;
      const [h, m] = o.start.split(':').map(Number);
      const startDate = parseKey(o.occurrenceDate);
      startDate.setHours(h, m, 0, 0);
      const triggerTime = startDate.getTime() - o.reminderMinutes * 60000;
      if (triggerTime <= now && now - triggerTime < 90000) {
        firedReminders.add(key);
        const body = [o.start + (o.end ? ' - ' + o.end : ''), o.memo].filter(Boolean).join('\n');
        window.api.notify(`🔔 ${o.title}`, body);
        showToast(o.title, body);
      }
    });
  }

  // ---------- in-app toast (fallback/redundant to OS notifications) ----------
  function ensureToastContainer() {
    let el = document.getElementById('toastContainer');
    if (!el) {
      el = document.createElement('div');
      el.id = 'toastContainer';
      el.className = 'toast-container';
      document.body.appendChild(el);
    }
    return el;
  }

  function showToast(title, body) {
    const container = ensureToastContainer();
    const toast = document.createElement('div');
    toast.className = 'toast';
    const titleEl = document.createElement('div');
    titleEl.className = 'toast-title';
    titleEl.textContent = `🔔 ${title}`;
    toast.appendChild(titleEl);
    if (body) {
      const bodyEl = document.createElement('div');
      bodyEl.className = 'toast-body';
      bodyEl.textContent = body;
      toast.appendChild(bodyEl);
    }
    const closeBtn = document.createElement('button');
    closeBtn.className = 'toast-close';
    closeBtn.textContent = '✕';
    closeBtn.addEventListener('click', () => toast.remove());
    toast.appendChild(closeBtn);

    container.appendChild(toast);
    setTimeout(() => toast.remove(), 12000);
  }

  init();
})();
