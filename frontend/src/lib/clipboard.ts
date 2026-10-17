/** Copy plaintext to the clipboard. Never log the value. */
export async function copyTextToClipboard(text: string): Promise<void> {
  await navigator.clipboard.writeText(text);
}
