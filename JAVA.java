/* Working Day Timetable Planner
 - registration (name or guest)
 - create/select/delete timetables
 - add/edit/mark done/delete tasks
 - persistence via localStorage
*/

// ---------- Elements ----------
const registerModal = document.getElementById('registerModal');
const regName = document.getElementById('regName');
const btnStart = document.getElementById('btnStart');
const btnGuest = document.getElementById('btnGuest');

const userGreeting = document.getElementById('userGreeting');
const btnNewTimetable = document.getElementById('btnNewTimetable');
const btnLogout = document.getElementById('btnLogout');

const timetableList = document.getElementById('timetableList');
const sidebarEmpty = document.getElementById('sidebarEmpty');
const searchTimetable = document.getElementById('searchTimetable');

const ttTitle = document.getElementById('ttTitle');
const ttMeta = document.getElementById('ttMeta');
const taskTime = document.getElementById('taskTime');
const taskTitle = document.getElementById('taskTitle');
const taskNote = document.getElementById('taskNote');
const btnAddTask = document.getElementById('btnAddTask');
const tasksArea = document.getElementById('tasksArea');

const btnDeleteTimetable = document.getElementById('btnDeleteTimetable');
const btnExport = document.getElementById('btnExport');

// ---------- Storage keys ----------
const USER_KEY = 'dayplanner_user_v1';
const TIMETABLES_KEY = 'dayplanner_timetables_v1';

// ---------- State ----------
let currentUser = null;
let timetables = []; // {id,title,createdAt,tasks:[]}
let activeTimetableId = null;

// ---------- Utils ----------
const uid = () => Date.now().toString(36) + Math.random().toString(36).slice(2, 8);
const now = () => new Date().toISOString();

function loadState() {
    const u = localStorage.getItem(USER_KEY);
    currentUser = u ? JSON.parse(u) : null;
    const t = localStorage.getItem(TIMETABLES_KEY);
    timetables = t ? JSON.parse(t) : [];
}

function saveState() {
    localStorage.setItem(TIMETABLES_KEY, JSON.stringify(timetables));
    if (currentUser) localStorage.setItem(USER_KEY, JSON.stringify(currentUser));
}

// ---------- Registration ----------
function openRegister() {
    registerModal.style.display = 'flex';
}

function closeRegister() {
    registerModal.style.display = 'none';
}

btnStart.addEventListener('click', () => {
    const name = regName.value.trim();
    if (!name) {
        alert('Please enter your name or continue as Guest.');
        regName.focus();
        return;
    }
    currentUser = {
        name,
        createdAt: now()
    };
    saveState();
    userGreeting.textContent = `Hello, ${currentUser.name}`;
    closeRegister();
    renderTimetableList();
    if (timetables.length) openTimetable(timetables[0].id);
});

btnGuest.addEventListener('click', () => {
    currentUser = {
        name: 'Guest',
        guest: true,
        createdAt: now()
    };
    saveState();
    userGreeting.textContent = `Hello, ${currentUser.name}`;
    closeRegister();
    renderTimetableList();
    if (timetables.length) openTimetable(timetables[0].id);
});

btnLogout.addEventListener('click', () => {
    if (confirm('Log out (this will only remove your name locally)?')) {
        currentUser = null;
        localStorage.removeItem(USER_KEY);
        openRegister();
        userGreeting.textContent = 'Hello';
    }
});

// ---------- Timetable CRUD ----------
btnNewTimetable.addEventListener('click', createTimetable);

function createTimetable() {
    const title = prompt('Timetable title (e.g., Monday, Exam Day):', `Timetable ${timetables.length+1}`);
    if (!title) return;
    const id = uid();
    const tt = {
        id,
        title: title.trim(),
        createdAt: now(),
        tasks: []
    };
    timetables.unshift(tt);
    saveState();
    activeTimetableId = id;
    renderTimetableList();
    openTimetable(id);
}

function renderTimetableList(filter = '') {
    timetableList.innerHTML = '';
    const visible = timetables.filter(tt => tt.title.toLowerCase().includes(filter.toLowerCase()));
    if (visible.length === 0) {
        sidebarEmpty.style.display = 'block';
    } else {
        sidebarEmpty.style.display = 'none';
        visible.forEach(tt => {
            const li = document.createElement('li');
            li.dataset.id = tt.id;
            li.className = (tt.id === activeTimetableId) ? 'active' : '';
            li.innerHTML = `<div class="meta"><strong>${escapeHtml(tt.title)}</strong><small class="muted">${new Date(tt.createdAt).toLocaleString()}</small></div>
                      <div class="actions">
                        <button class="btn tiny edit-tt">Edit</button>
                        <button class="btn tiny ghost export-tt">Export</button>
                        <button class="btn tiny danger del-tt">Delete</button>
                      </div>`;
            // select
            li.addEventListener('click', (e) => {
                if (e.target.closest('.actions')) return;
                activeTimetableId = tt.id;
                renderTimetableList(searchTimetable.value || '');
                openTimetable(tt.id);
            });
            // edit title
            li.querySelector('.edit-tt').addEventListener('click', (ev) => {
                ev.stopPropagation();
                const newTitle = prompt('Edit timetable title', tt.title);
                if (newTitle && newTitle.trim()) {
                    tt.title = newTitle.trim();
                    saveState();
                    renderTimetableList(searchTimetable.value || '');
                    if (activeTimetableId === tt.id) openTimetable(tt.id);
                }
            });
            // export
            li.querySelector('.export-tt').addEventListener('click', (ev) => {
                ev.stopPropagation();
                const blob = new Blob([JSON.stringify(tt, null, 2)], {
                    type: 'application/json'
                });
                const url = URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = `${tt.title.replace(/\s+/g,'_')}.json`;
                a.click();
                URL.revokeObjectURL(url);
            });
            // delete
            li.querySelector('.del-tt').addEventListener('click', (ev) => {
                ev.stopPropagation();
                if (!confirm(`Delete "${tt.title}"?`)) return;
                timetables = timetables.filter(x => x.id !== tt.id);
                if (activeTimetableId === tt.id) {
                    activeTimetableId = null;
                    clearMain();
                }
                saveState();
                renderTimetableList(searchTimetable.value || '');
            });

            timetableList.appendChild(li);
        });
    }
}

searchTimetable.addEventListener('input', () => renderTimetableList(searchTimetable.value || ''));

// ---------- Open / Display timetable ----------
function openTimetable(id) {
    const tt = timetables.find(t => t.id === id);
    if (!tt) return clearMain();
    activeTimetableId = id;
    ttTitle.textContent = tt.title;
    ttMeta.textContent = `Created: ${new Date(tt.createdAt).toLocaleString()} · ${tt.tasks.length} tasks`;
    btnDeleteTimetable.classList.remove('hidden');
    btnExport.classList.remove('hidden');
    renderTimetableList(searchTimetable.value || '');
    renderTasks(tt);
}

function clearMain() {
    activeTimetableId = null;
    ttTitle.textContent = 'Select or Create a Timetable';
    ttMeta.textContent = 'Your timetables appear here.';
    btnDeleteTimetable.classList.add('hidden');
    btnExport.classList.add('hidden');
    tasksArea.innerHTML = `<p class="muted">No timetable selected.</p>`;
}

// ---------- Tasks management ----------
btnAddTask.addEventListener('click', () => {
    if (!activeTimetableId) {
        alert('Select or create a timetable first.');
        return;
    }
    const timeVal = taskTime.value || '';
    const titleVal = (taskTitle.value || '').trim();
    const noteVal = (taskNote.value || '').trim();
    if (!titleVal) {
        alert('Task title required');
        taskTitle.focus();
        return;
    }
    const tt = timetables.find(t => t.id === activeTimetableId);
    const task = {
        id: uid(),
        time: timeVal,
        title: titleVal,
        note: noteVal,
        done: false,
        createdAt: now()
    };
    tt.tasks.push(task);
    saveState();
    renderTasks(tt);
    taskTime.value = '';
    taskTitle.value = '';
    taskNote.value = '';
});

function renderTasks(tt) {
    tasksArea.innerHTML = '';
    if (!tt.tasks || tt.tasks.length === 0) {
        tasksArea.innerHTML = `<p class="muted">No tasks yet. Add a task using the inputs above.</p>`;
        ttMeta.textContent = `Created: ${new Date(tt.createdAt).toLocaleString()} · 0 tasks`;
        return;
    }
    // sort by time if set
    const sorted = [...tt.tasks].sort((a, b) => {
        if (a.time && b.time) return a.time.localeCompare(b.time);
        if (a.time) return -1;
        if (b.time) return 1;
        return new Date(a.createdAt) - new Date(b.createdAt);
    });

    sorted.forEach(task => {
        const row = document.createElement('div');
        row.className = 'task-row' + (task.done ? ' done' : '');
        row.dataset.id = task.id;

        const left = document.createElement('div');
        left.className = 'task-left';
        const timeEl = document.createElement('div');
        timeEl.className = 'time';
        timeEl.textContent = task.time || '—';
        const titleEl = document.createElement('div');
        titleEl.className = 'title';
        titleEl.textContent = task.title;
        const noteEl = document.createElement('div');
        noteEl.className = 'note muted';
        noteEl.textContent = task.note || '';

        left.appendChild(timeEl);
        left.appendChild(titleEl);
        left.appendChild(noteEl);

        const actions = document.createElement('div');
        actions.className = 'task-actions';
        const btnEdit = document.createElement('button');
        btnEdit.className = 'btn tiny edit';
        btnEdit.textContent = 'Edit';
        const btnToggle = document.createElement('button');
        btnToggle.className = 'btn tiny toggle-done';
        btnToggle.textContent = task.done ? 'Undo' : 'Done';
        const btnDelete = document.createElement('button');
        btnDelete.className = 'btn tiny delete';
        btnDelete.textContent = 'Delete';

        actions.appendChild(btnEdit);
        actions.appendChild(btnToggle);
        actions.appendChild(btnDelete);
        row.appendChild(left);
        row.appendChild(actions);

        // Edit handler: inline replace with inputs
        btnEdit.addEventListener('click', () => {
            const inputTime = document.createElement('input');
            inputTime.type = 'time';
            inputTime.className = 'edit-input';
            inputTime.value = task.time || '';
            const inputTitle = document.createElement('input');
            inputTitle.className = 'edit-input';
            inputTitle.value = task.title;
            const inputNote = document.createElement('input');
            inputNote.className = 'edit-input';
            inputNote.value = task.note || '';

            left.innerHTML = '';
            left.appendChild(inputTime);
            left.appendChild(inputTitle);
            left.appendChild(inputNote);
            btnEdit.textContent = 'Save';
            btnEdit.classList.add('primary');

            const saveFn = () => {
                const newTitle = inputTitle.value.trim();
                if (!newTitle) {
                    alert('Title required');
                    inputTitle.focus();
                    return;
                }
                task.time = inputTime.value || '';
                task.title = newTitle;
                task.note = inputNote.value.trim();
                saveState();
                renderTasks(tt);
            };
            btnEdit.removeEventListener('click', () => {}); // safe no-op
            btnEdit.addEventListener('click', saveFn, {
                once: true
            });
        });

        btnToggle.addEventListener('click', () => {
            task.done = !task.done;
            saveState();
            renderTasks(tt);
        });

        btnDelete.addEventListener('click', () => {
            if (!confirm('Delete this task?')) return;
            tt.tasks = tt.tasks.filter(tk => tk.id !== task.id);
            saveState();
            renderTasks(tt);
        });

        tasksArea.appendChild(row);
    });

    ttMeta.textContent = `Created: ${new Date(tt.createdAt).toLocaleString()} · ${tt.tasks.length} tasks`;
}

// ---------- Delete and Export active timetable ----------
btnDeleteTimetable.addEventListener('click', () => {
    if (!activeTimetableId) return;
    const tt = timetables.find(t => t.id === activeTimetableId);
    if (!confirm(`Delete timetable "${tt.title}"? This cannot be undone.`)) return;
    timetables = timetables.filter(t => t.id !== activeTimetableId);
    activeTimetableId = null;
    saveState();
    renderTimetableList();
    clearMain();
});

btnExport.addEventListener('click', () => {
    if (!activeTimetableId) return;
    const tt = timetables.find(t => t.id === activeTimetableId);
    const blob = new Blob([JSON.stringify(tt, null, 2)], {
        type: 'application/json'
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${tt.title.replace(/\s+/g,'_')}_timetable.json`;
    a.click();
    URL.revokeObjectURL(url);
});

// ---------- helpers ----------
function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, c => ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#39;'
    } [c]));
}

// ---------- boot ----------
function boot() {
    loadState();
    if (currentUser && currentUser.name) {
        closeRegister();
        userGreeting.textContent = `Hello, ${currentUser.name}`;
    } else {
        openRegister();
    }
    renderTimetableList();
    if (timetables.length > 0) {
        if (!activeTimetableId) activeTimetableId = timetables[0].id;
        openTimetable(activeTimetableId);
    } else {
        clearMain();
    }
}

document.addEventListener('DOMContentLoaded', boot);