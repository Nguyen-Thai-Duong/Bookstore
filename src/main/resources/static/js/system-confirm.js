(function () {
    function createDialog() {
        const backdrop = document.createElement('div');
        backdrop.id = 'system-confirm-backdrop';
        backdrop.style.cssText = [
            'position:fixed',
            'inset:0',
            'background:rgba(15,23,42,0.45)',
            'display:none',
            'align-items:center',
            'justify-content:center',
            'z-index:9999',
            'padding:16px'
        ].join(';');

        const panel = document.createElement('div');
        panel.style.cssText = [
            'width:min(460px,100%)',
            'background:#fff',
            'border-radius:14px',
            'box-shadow:0 20px 45px rgba(0,0,0,0.25)',
            'padding:22px',
            'font-family:Inter,Segoe UI,Arial,sans-serif'
        ].join(';');

        const title = document.createElement('h3');
        title.textContent = 'Please confirm';
        title.style.cssText = 'margin:0 0 10px;font-size:20px;font-weight:700;color:#111827;';

        const message = document.createElement('p');
        message.id = 'system-confirm-message';
        message.style.cssText = 'margin:0 0 18px;font-size:15px;line-height:1.45;color:#4b5563;';

        const actions = document.createElement('div');
        actions.style.cssText = 'display:flex;justify-content:flex-end;gap:10px;';

        const cancelBtn = document.createElement('button');
        cancelBtn.type = 'button';
        cancelBtn.textContent = 'Cancel';
        cancelBtn.style.cssText = [
            'padding:9px 14px',
            'border:1px solid #d1d5db',
            'border-radius:10px',
            'background:#fff',
            'color:#374151',
            'font-weight:600',
            'cursor:pointer'
        ].join(';');

        const confirmBtn = document.createElement('button');
        confirmBtn.type = 'button';
        confirmBtn.textContent = 'Confirm';
        confirmBtn.style.cssText = [
            'padding:9px 14px',
            'border:0',
            'border-radius:10px',
            'background:#8b5a3c',
            'color:#fff',
            'font-weight:700',
            'cursor:pointer'
        ].join(';');

        actions.appendChild(cancelBtn);
        actions.appendChild(confirmBtn);
        panel.appendChild(title);
        panel.appendChild(message);
        panel.appendChild(actions);
        backdrop.appendChild(panel);
        document.body.appendChild(backdrop);

        return { backdrop, message, cancelBtn, confirmBtn };
    }

    function openConfirm(text) {
        if (!window.__systemConfirmDialog) {
            window.__systemConfirmDialog = createDialog();
        }

        const dialog = window.__systemConfirmDialog;
        dialog.message.textContent = text || 'Are you sure you want to continue?';
        dialog.backdrop.style.display = 'flex';

        return new Promise(function (resolve) {
            function cleanup(result) {
                dialog.backdrop.style.display = 'none';
                dialog.confirmBtn.removeEventListener('click', onConfirm);
                dialog.cancelBtn.removeEventListener('click', onCancel);
                dialog.backdrop.removeEventListener('click', onBackdrop);
                document.removeEventListener('keydown', onEsc);
                resolve(result);
            }

            function onConfirm() {
                cleanup(true);
            }

            function onCancel() {
                cleanup(false);
            }

            function onBackdrop(e) {
                if (e.target === dialog.backdrop) {
                    cleanup(false);
                }
            }

            function onEsc(e) {
                if (e.key === 'Escape') {
                    cleanup(false);
                }
            }

            dialog.confirmBtn.addEventListener('click', onConfirm);
            dialog.cancelBtn.addEventListener('click', onCancel);
            dialog.backdrop.addEventListener('click', onBackdrop);
            document.addEventListener('keydown', onEsc);
            dialog.confirmBtn.focus();
        });
    }

    document.addEventListener('click', function (e) {
        const trigger = e.target.closest('[data-confirm]');
        if (!trigger) {
            return;
        }

        e.preventDefault();
        const message = trigger.getAttribute('data-confirm') || 'Are you sure?';

        openConfirm(message).then(function (ok) {
            if (!ok) {
                return;
            }

            if (trigger.tagName === 'A' && trigger.href) {
                window.location.href = trigger.href;
                return;
            }

            if (trigger.tagName === 'BUTTON') {
                const form = trigger.form || trigger.closest('form');
                if (form) {
                    form.submit();
                }
            }
        });
    });
})();
