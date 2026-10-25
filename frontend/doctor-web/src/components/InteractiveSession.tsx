"use client";
import {useEffect} from "react";
import {useAuth} from "@/contexts/AuthContext";
import {api} from "@/lib/api";

/** Explicit user gestures extend inactivity; fetching/polling/refresh do not. */
export default function InteractiveSession() {
  const {user,isAuthenticated,logout}=useAuth();
  useEffect(()=>{
    if(!isAuthenticated)return;
    const timeout=user?.role==="PATIENT"?30*60_000:15*60_000;
    let timer:ReturnType<typeof setTimeout>,lastHeartbeat=0,lastInteraction=Date.now(),closing=false;
    async function lock() {
      if(closing)return;closing=true;
      try {await logout();} catch {/* Logout already clears local state and redirects. */}
    }
    function interaction(event?:Event) {
      if(event && !event.isTrusted)return;
      lastInteraction=Date.now();clearTimeout(timer);timer=setTimeout(()=>void lock(),timeout);
      if(!event||document.visibilityState!=="visible")return;
      if(Date.now()-lastHeartbeat<60_000)return;lastHeartbeat=Date.now();
      void api.post("/api/auth/activity",{}).catch(()=>{/* Server rejects expired sessions; no automatic activity retry. */});
    }
    function visibility(){if(document.visibilityState==="visible"&&Date.now()-lastInteraction>=timeout)void lock();}
    interaction();lastHeartbeat=Date.now();
    const events=["pointerdown","keydown","wheel","touchstart"];
    for(const event of events)window.addEventListener(event,interaction,{passive:true});
    document.addEventListener("visibilitychange",visibility);
    window.addEventListener("careflow:session-expired",lock);
    return()=>{clearTimeout(timer);for(const event of events)window.removeEventListener(event,interaction);document.removeEventListener("visibilitychange",visibility);window.removeEventListener("careflow:session-expired",lock);};
  },[isAuthenticated,user?.role,logout]);
  return null;
}
