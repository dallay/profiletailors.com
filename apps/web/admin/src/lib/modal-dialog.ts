export function syncModalDialog(dialog: HTMLDialogElement | null, open: boolean): void {
  if (dialog === null) return
  if (open && !dialog.open) {
    if (typeof dialog.showModal === 'function') dialog.showModal()
    else dialog.setAttribute('open', '')
    return
  }
  if (!open && dialog.open) {
    if (typeof dialog.close === 'function') dialog.close()
    else dialog.removeAttribute('open')
  }
}
