import { Component, type ErrorInfo, type ReactNode } from 'react';

type Props = {
  children: ReactNode;
};

type State = {
  hasError: boolean;
};

export class AppErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false };

  static getDerivedStateFromError(): State {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    // Keep logs free of tokens/PII; message + component stack only.
    console.error('PayFlow UI error:', error.message, info.componentStack);
  }

  render(): ReactNode {
    if (this.state.hasError) {
      return (
        <main className="mx-auto flex min-h-screen max-w-lg flex-col justify-center gap-3 px-6">
          <h1 className="text-2xl font-semibold text-slate-900">Something went wrong</h1>
          <p className="text-slate-600">
            The PayFlow UI hit an unexpected error. Refresh the page to continue.
          </p>
          <button
            type="button"
            className="w-fit rounded-md bg-teal-800 px-4 py-2 text-sm font-medium text-white"
            onClick={() => this.setState({ hasError: false })}
          >
            Try again
          </button>
        </main>
      );
    }
    return this.props.children;
  }
}
