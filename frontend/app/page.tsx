"use client"

import type React from "react"

import { useState, useEffect, useRef } from "react"
import { Server, Users, Wifi, WifiOff, LogIn, LogOut, ArrowUp, ArrowDown, Loader2Icon  } from "lucide-react"
import {
    Sidebar,
    SidebarContent,
    SidebarGroup,
    SidebarGroupContent,
    SidebarGroupLabel,
    SidebarHeader,
    SidebarInset,
    SidebarMenu,
    SidebarMenuButton,
    SidebarMenuItem,
    SidebarProvider,
    SidebarTrigger,
} from "@/components/ui/sidebar"
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "@/components/ui/dropdown-menu"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Separator } from "@/components/ui/separator"
import { ScrollArea } from "@/components/ui/scroll-area"
import { toast } from "sonner"

// Add STOMP.js types
declare global {
    interface Window {
        StompJs: any
    }
}

interface StatusMessage {
    id: number
    username: string
    statustext: string
    timestamp: string
}

const servers = [
    { name: "Server 1", port: 9001, },
    { name: "Server 2", port: 9002, },
]

export default function StatusServerApp() {
    const [selectedServer, setSelectedServer] = useState(servers[0])
    const [isConnected, setIsConnected] = useState(false)
    const [statuses, setStatuses] = useState<StatusMessage[]>([])
    const [username, setUsername] = useState("")
    const [connectedUsername, setConnectedUsername] = useState("")
    const [customStatus, setCustomStatus] = useState("")
    const [currentUserStatus, setCurrentUserStatus] = useState<string>()
    const [isConnectionLoading, setIsConnectionLoading] = useState(false)
    const stompClientRef = useRef<any>(null)

    // Load STOMP.js script
    useEffect(() => {
        const script = document.createElement("script")
        script.src = "https://cdn.jsdelivr.net/npm/@stomp/stompjs@7.0.0/bundles/stomp.umd.min.js"
        script.async = true
        document.head.appendChild(script)

        return () => {
            document.head.removeChild(script)
        }
    }, [])

    const isUsernameTakenGlobally = async (name: string) => {
        for (const server of servers) {
            try {
                const res = await fetch(`http://localhost:${server.port}/status/username-active?username=${encodeURIComponent(name)}`);
                const isActive = await res.json();
                if (isActive) return true;
            } catch (err) {
                console.warn(`Could not check server ${server.port}:`, err);
            }
        }
        return false;
    };

    const connectToServer = async () => {
        setIsConnectionLoading(true)
        if (!username.trim() || !window.StompJs) return

        const taken = await isUsernameTakenGlobally(username);
        if (taken) {
            return;
        }


        try {
            const gatewayWsUrl = `ws://localhost:${selectedServer.port}/status-websocket-${selectedServer.port}`

            stompClientRef.current = new window.StompJs.Client({
                brokerURL: gatewayWsUrl,
                reconnectDelay: 5000,
                onConnect: (frame: any) => {
                    setIsUsernameInUse(false);
                    console.log(`Connected to port ${selectedServer.port}:`, frame)
                    setIsConnected(true)
                    setConnectedUsername(username)

                    // Subscribe to status updates
                    stompClientRef.current.subscribe("/topic/status", (message: any) => {
                        const status = JSON.parse(message.body)
                        updateStatus(status)
                    })

                    // Subscribe to initial status load
                    stompClientRef.current.subscribe("/topic/init-status", (message: any) => {
                        const status = JSON.parse(message.body)
                        updateStatus(status)
                    })

                    // Subscribe to status deletions
                    stompClientRef.current.subscribe("/topic/status-delete", (message: any) => {
                        const id = Number(message.body)
                        setStatuses((prev) => prev.filter((s) => s.id !== id))
                    })

                    // Subscribe to status errors
                    stompClientRef.current.subscribe("/user/queue/errors", (message: any) => {
                        alert("Error: " + message.body)
                        disconnectFromServer()
                    })

                    // Request existing statuses
                    stompClientRef.current.publish({
                        destination: "/app/request-statuses",
                        body: "",
                    })

                    // Send initial "Connected" status (after delay to ensure that subscriptions are ready)
                    setTimeout(() => {
                        const request = {
                            username: username,
                            statusText: "Connected",
                        }

                        stompClientRef.current.publish({
                            destination: "/app/status",
                            body: JSON.stringify(request),
                        })

                        console.log("[SEND] Initial connected status:", request)
                    }, 500)
                    setIsConnectionLoading(false)
                },
                onStompError: (frame: any) => {
                    console.error(`Broker error: ${frame.headers["message"]}`)
                    console.error("Details:", frame.body)
                    setIsConnected(false)
                    setConnectedUsername("")
                },
                onWebSocketError: (error: any) => {
                    console.error(`WebSocket error:`, error)
                    toast.error("Failed to connect to the server", {
                        description: "It appears the server is down. Please try again later.",
                        action: {
                            label: "Undo",
                            onClick: () => setUsername(""),
                        }
                    })
                    setIsConnected(false)
                    setIsConnectionLoading(false)
                    setUsername("")
                    setConnectedUsername("")
                    stompClientRef.current.deactivate()
                },
            })

            stompClientRef.current.activate()
        } catch (error) {
            console.error("Failed to connect:", error)
            toast.error("Failed to connect to the server", {
                description: "It appears the server is down. Please try again later.",
                action: {
                    label: "Undo",
                    onClick: () => setUsername(""),
                }
            })
            setIsConnected(false)
            setIsConnectionLoading(false)
            setConnectedUsername("")
        }
    }

    const disconnectFromServer = () => {
        if (stompClientRef.current && stompClientRef.current.connected && connectedUsername) {
            stompClientRef.current.publish({
                destination: "/app/disconnect",
                body: JSON.stringify({ username: connectedUsername }),
            })
        }
            stompClientRef.current.deactivate()
            stompClientRef.current = null
            setIsConnected(false)
            setConnectedUsername("")
            setCurrentUserStatus(undefined)
            setStatuses([])
            console.log("Disconnected")
    }

    const updateStatus = (status: StatusMessage) => {
        setStatuses((prev) => {
            const existingIndex = prev.findIndex((s) => s.id === status.id)
            if (existingIndex >= 0) {
                // Update existing status
                const updated = [...prev]
                updated[existingIndex] = status
                return updated
            } else {
                // Add new status
                return [status, ...prev]
            }
        })

        // Update current user status if it's their status
        if (status.username === connectedUsername) {
            setCurrentUserStatus(status.statustext)
        }
    }

    const sendStatus = (statusText: string) => {
        if (!stompClientRef.current?.connected || !connectedUsername) return

        const request = {
            username: connectedUsername,
            statusText: statusText,
        }

        stompClientRef.current.publish({
            destination: "/app/status",
            body: JSON.stringify(request),
        })

        console.log("[SEND] Sent status:", request)
    }

    // Clean up on unmount
    useEffect(() => {
        return () => {
            if (stompClientRef.current && stompClientRef.current.active) {
                stompClientRef.current.deactivate()
            }
        }
    }, [])

    const handleKeyPress = (e: React.KeyboardEvent) => {
        if (e.key === "Enter" && !e.shiftKey) {
            e.preventDefault()
            if (!isConnected) {
                connectToServer()
            }
        }
    }

    const [isUsernameInUse, setIsUsernameInUse] = useState(false)

    return (
        <SidebarProvider>
            <Sidebar>
                <SidebarHeader>
                    <SidebarMenu>
                        <SidebarMenuItem>
                            <SidebarMenuButton size="lg" className="data-[state=open]:bg-sidebar-accent">
                                <div className="flex aspect-square size-8 items-center justify-center rounded-lg bg-sidebar-primary text-sidebar-primary-foreground">
                                    <Server className="size-4" />
                                </div>
                                <div className="flex flex-col gap-0.5 leading-none">
                                    <span className="font-semibold">Status Server</span>
                                    <span className="text-xs">STOMP WebSocket Client</span>
                                </div>
                            </SidebarMenuButton>
                        </SidebarMenuItem>
                    </SidebarMenu>
                </SidebarHeader>

                <SidebarContent>
                    <SidebarGroup>
                        <SidebarGroupLabel>Server Selection</SidebarGroupLabel>
                        <SidebarGroupContent>
                            <DropdownMenu>
                                <DropdownMenuTrigger asChild>
                                    <Button variant="outline" className="w-full justify-between" disabled={isConnected}>
                                        <div className="flex items-center gap-2">
                                            {isConnected ? (
                                                <Wifi className="h-4 w-4 text-green-500" />
                                            ) : (
                                                <WifiOff className="h-4 w-4 text-red-500" />
                                            )}
                                            {selectedServer.name}
                                        </div>
                                    </Button>
                                </DropdownMenuTrigger>
                                <DropdownMenuContent className="w-full">
                                    {servers.map((server) => (
                                        <DropdownMenuItem
                                            key={server.port}
                                            onClick={() => setSelectedServer(server)}
                                            className="flex flex-col items-start gap-1"
                                        >
                                            <span className="font-medium">{server.name}</span>
                                            <span className="text-xs text-muted-foreground">Port {server.port}</span>
                                        </DropdownMenuItem>
                                    ))}
                                </DropdownMenuContent>
                            </DropdownMenu>
                        </SidebarGroupContent>
                    </SidebarGroup>

                    <SidebarGroup>
                        <SidebarGroupLabel>Connection Status</SidebarGroupLabel>
                        <SidebarGroupContent>
                            <div className="flex items-center gap-2 p-2 rounded-md bg-muted/50">
                                {isConnected ? (
                                    <>
                                        <div className="h-2 w-2 rounded-full bg-green-500 animate-pulse" />
                                        <span className="text-sm text-green-700">Connected</span>
                                    </>
                                ) : (
                                    <>
                                        <div className="h-2 w-2 rounded-full bg-red-500" />
                                        <span className="text-sm text-red-700">Disconnected</span>
                                    </>
                                )}
                            </div>
                            {isConnected && (
                                <>
                                    <div className="text-xs text-muted-foreground mt-2">
                                        Logged in as: <span className="font-medium">{connectedUsername}</span>
                                    </div>
                                    {currentUserStatus && (
                                        <div className="text-xs text-muted-foreground mt-1">
                                            Your status:{" "}
                                            <Badge variant={currentUserStatus === "DOWN" ? "destructive" : "default"} className="text-xs">
                                                {currentUserStatus}
                                            </Badge>
                                        </div>
                                    )}
                                </>
                            )}
                            <div className="text-xs text-muted-foreground mt-1">{statuses.length} status entries</div>
                        </SidebarGroupContent>
                    </SidebarGroup>

                    {isConnected && (
                        <SidebarGroup>
                            <SidebarGroupContent>
                                <Button variant="outline" onClick={disconnectFromServer} className="w-full">
                                    <LogOut className="h-4 w-4 mr-2" />
                                    Disconnect
                                </Button>
                            </SidebarGroupContent>
                        </SidebarGroup>
                    )}
                </SidebarContent>
            </Sidebar>

            <SidebarInset>
                <header className="flex h-16 shrink-0 items-center gap-2 border-b px-4">
                    <SidebarTrigger className="-ml-1" />
                    <Separator orientation="vertical" className="mr-2 h-4" />
                    <div className="flex items-center gap-2">
                        <Users className="h-4 w-4" />
                        <span className="font-semibold">Status Dashboard</span>
                    </div>
                </header>

                <div className="flex flex-1 flex-col gap-4 p-4">
                    {/* Connection/Status Form */}
                    <Card>
                        <CardHeader>
                            <CardTitle className="flex items-center gap-2">
                                {isConnected ? (
                                    <>
                                        <Users className="h-5 w-5" />
                                        Update Your Status
                                    </>
                                ) : (
                                    <>
                                        <LogIn className="h-5 w-5" />
                                        Connect to Server
                                    </>
                                )}
                            </CardTitle>
                        </CardHeader>
                        <CardContent className="space-y-4">
                            {!isConnected ? (
                                <div className="space-y-4">
                                    <div className="space-y-2">
                                        <label htmlFor="username" className="text-sm font-medium">
                                            Username
                                        </label>
                                        <Input
                                            id="username"
                                            placeholder="Enter your username"
                                            value={username}
                                            onChange={async (e) => {
                                                const newUsername = e.target.value
                                                setUsername(newUsername)

                                                if (newUsername.trim()) {
                                                    try {
                                                        const taken = await isUsernameTakenGlobally(newUsername)
                                                        setIsUsernameInUse(taken)
                                                    } catch (err) {
                                                        console.error("Username check failed:", err)
                                                        setIsUsernameInUse(false)
                                                    }
                                                } else {
                                                    setIsUsernameInUse(false)
                                                }
                                            }}
                                            onKeyDown={handleKeyPress}
                                        />
                                        {isUsernameInUse && (
                                            <div className="text-sm text-red-600 bg-red-100 p-2 rounded">
                                                This username is already connected on this or another server.
                                            </div>
                                        )}
                                    </div>
                                    <Button onClick={connectToServer} disabled={!username.trim() || isUsernameInUse} className="w-full">
                                        {isConnectionLoading ? (
                                            <span className="animate-spin">
                                                <Loader2Icon  className="h-4 w-4" />
                                            </span>
                                        ) : (
                                            <>
                                                <LogIn className="h-4 w-4 mr-2" />
                                                Connect to {selectedServer.name}
                                            </>
                                        )}
                                    </Button>
                                </div>
                            ) : (
                                <div className="space-y-4">
                                    <div className="flex items-center gap-2 p-2 rounded-md bg-muted/50">
                                        <Users className="h-4 w-4" />
                                        <span className="text-sm">
                                            Connected as: <span className="font-medium">{connectedUsername}</span>
                                        </span>
                                        {currentUserStatus && (
                                            <Badge variant={currentUserStatus === "UP" ? "default" : "destructive"}>
                                                {currentUserStatus}
                                            </Badge>
                                        )}
                                    </div>
                                    <div className="flex gap-2">
                                        <Button
                                            onClick={() => sendStatus("UP")}
                                            disabled={currentUserStatus === "UP"}
                                            className="flex-1"
                                            variant={currentUserStatus === "UP" ? "default" : "outline"}
                                        >
                                            <ArrowUp className="h-4 w-4 mr-2" />
                                            Set UP
                                        </Button>
                                        <Button
                                            onClick={() => sendStatus("DOWN")}
                                            disabled={currentUserStatus === "DOWN"}
                                            className="flex-1"
                                            variant={currentUserStatus === "DOWN" ? "destructive" : "outline"}
                                        >
                                            <ArrowDown className="h-4 w-4 mr-2" />
                                            Set DOWN
                                        </Button>
                                    </div>

                                    {/* Custom Status Input */}
                                    <div className="flex gap-2">
                                        <Input
                                            placeholder="Enter custom status..."
                                            value={customStatus}
                                            onChange={(e) => setCustomStatus(e.target.value)}
                                            className="flex-1"
                                            onKeyDown={(e) => {
                                                if (e.key === "Enter" && customStatus.trim()) {
                                                    e.preventDefault();
                                                    sendStatus(customStatus.trim());
                                                    setCustomStatus("");
                                                }
                                            }}
                                        />
                                        <Button
                                            onClick={() => {
                                                if (customStatus.trim()) {
                                                    sendStatus(customStatus.trim());
                                                    setCustomStatus("");
                                                }
                                            }}
                                            disabled={!customStatus.trim() || !connectedUsername}
                                        >
                                            Send
                                        </Button>
                                    </div>
                                </div>
                            )}
                        </CardContent>
                    </Card>

                    {/* Status Messages */}
                    <Card className="flex-1">
                        <CardHeader>
                            <CardTitle className="flex items-center justify-between">
                                <span>Live Status Feed</span>
                                <Badge variant="secondary">{statuses.length} entries</Badge>
                            </CardTitle>
                        </CardHeader>
                        <CardContent>
                            <ScrollArea className="h-[500px] w-full">
                                {statuses.length === 0 ? (
                                    <div className="flex flex-col items-center justify-center h-32 text-muted-foreground">
                                        <Users className="h-8 w-8 mb-2" />
                                        <p>No status entries yet</p>
                                        <p className="text-sm">
                                            {isConnected ? "Start updating your status!" : "Connect to a server to see live updates"}
                                        </p>
                                    </div>
                                ) : (
                                    <div className="space-y-3">
                                        {statuses.map((statusMsg) => (
                                            <div
                                                key={statusMsg.id}
                                                className="flex items-start gap-3 p-3 rounded-lg border bg-card hover:bg-muted/50 transition-colors"
                                            >
                                                <div className="flex-1 space-y-1">
                                                    <div className="flex items-center gap-2">
                                                        <span className="font-medium text-sm">{statusMsg.username}</span>
                                                        <Badge
                                                            variant={
                                                                statusMsg.statustext === "DOWN" ? "destructive" :
                                                                    statusMsg.statustext === "Inactive" ? "secondary" :
                                                                        statusMsg.statustext === "Connected" ? "secondary" :
                                                                            "default"
                                                            }
                                                            className="text-xs"
                                                        >
                                                            {statusMsg.statustext}
                                                        </Badge>
                                                        <Badge variant="outline" className="text-xs">
                                                            ID: {statusMsg.id}
                                                        </Badge>
                                                        <span className="text-xs text-muted-foreground">
                                                            {new Date(statusMsg.timestamp).toLocaleString()}
                                                        </span>
                                                    </div>
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                )}
                            </ScrollArea>
                        </CardContent>
                    </Card>
                </div>
            </SidebarInset>
        </SidebarProvider>
    )
}
