import type { Metadata } from 'next'
import '@/styles/globals.css'
import { Toaster } from "@/components/ui/sonner"

export const metadata: Metadata = {
    title: 'Status Server Demo',
    description: 'A demo application for the Status Server',
}

export default function RootLayout({
    children,
}: Readonly<{
    children: React.ReactNode
}>) {
    return (
        <html lang="en">
            <body>
                {children}
                <Toaster
                    position="top-center"
                    expand={false}
                />
            </body>
        </html>
    )
}
