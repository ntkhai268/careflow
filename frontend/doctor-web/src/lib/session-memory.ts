// Tokens are never persisted into localStorage/sessionStorage or exposed through a refresh cookie to JavaScript.
let accessToken: string | null = null;
let sessionEpoch = 0;
export const getSessionEpoch = () => sessionEpoch;
export const getAccessToken = () => accessToken;
export const setAccessToken = (value: string | null) => { accessToken = value; sessionEpoch++; };
export function clearLegacySession() {
  for (const key of ["careflow_token", "careflow_refresh_token", "careflow_user"]) localStorage.removeItem(key);
}
