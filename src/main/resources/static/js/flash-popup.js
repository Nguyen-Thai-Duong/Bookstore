document.addEventListener('DOMContentLoaded', () => {
    const popup = document.querySelector('[data-flash-popup]');
    if (!popup) {
        return;
    }

    popup.classList.remove('hidden');

    const closeButtons = popup.querySelectorAll('[data-flash-close]');
    const dismiss = () => {
        popup.classList.add('hidden');
    };

    closeButtons.forEach((button) => {
        button.addEventListener('click', dismiss);
    });

    popup.addEventListener('click', (event) => {
        if (event.target === popup) {
            dismiss();
        }
    });
});