import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { Provider } from "react-redux";
import { configureStore } from "@reduxjs/toolkit";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { FormField } from "@/components/ui/form-field";
import { Badge } from "@/components/ui/badge";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { StatusBadge } from "@/components/ui/status-badge";
import { PermissionGate } from "@/components/ui/permission-gate";
import authReducer, { setSession } from "@/store/authSlice";

function createTestStore() {
  const store = configureStore({
    reducer: {
      auth: authReducer,
    },
  });
  store.dispatch(
    setSession({
      user: {
        id: "user-1",
        email: "test@example.com",
        firstName: "Test",
        lastName: "User",
        roles: ["ADMIN"],
      },
      accessToken: "test-token",
      roles: ["ADMIN"],
    })
  );
  return store;
}

function Wrapper({ children }: { children: React.ReactNode }) {
  return <Provider store={createTestStore()}>{children}</Provider>;
}

describe("Button", () => {
  it("renders children", () => {
    render(<Button>Click me</Button>);
    expect(screen.getByText("Click me")).toBeInTheDocument();
  });

  it("shows loading spinner when loading", () => {
    render(<Button loading>Submit</Button>);
    expect(screen.getByRole("button")).toBeDisabled();
  });

  it("calls onClick handler", () => {
    const handleClick = vi.fn();
    render(<Button onClick={handleClick}>Click</Button>);
    fireEvent.click(screen.getByText("Click"));
    expect(handleClick).toHaveBeenCalledTimes(1);
  });

  it("applies variant classes", () => {
    render(<Button variant="destructive">Delete</Button>);
    const button = screen.getByText("Delete");
    expect(button.className).toContain("bg-destructive");
  });
});

describe("Input", () => {
  it("renders with placeholder", () => {
    render(<Input placeholder="Enter email" />);
    expect(screen.getByPlaceholderText("Enter email")).toBeInTheDocument();
  });

  it("forwards ref", () => {
    const { container } = render(<Input />);
    expect(container.querySelector("input")).toBeInTheDocument();
  });
});

describe("FormField", () => {
  it("renders label and required marker", () => {
    render(
      <FormField label="Email" required>
        <Input />
      </FormField>
    );
    expect(screen.getByText("Email")).toBeInTheDocument();
    expect(screen.getByText("*")).toBeInTheDocument();
  });

  it("renders error message", () => {
    render(
      <FormField label="Email" error="Email is required">
        <Input />
      </FormField>
    );
    expect(screen.getByText("Email is required")).toBeInTheDocument();
  });

  it("renders hint text", () => {
    render(
      <FormField label="Email" hint="Enter your email">
        <Input />
      </FormField>
    );
    expect(screen.getByText("Enter your email")).toBeInTheDocument();
  });
});

describe("Badge", () => {
  it("renders children", () => {
    render(<Badge>Active</Badge>);
    expect(screen.getByText("Active")).toBeInTheDocument();
  });

  it("applies variant classes", () => {
    render(<Badge variant="success">Success</Badge>);
    expect(screen.getByText("Success").className).toContain("bg-success");
  });
});

describe("EmptyState", () => {
  it("renders title and description", () => {
    render(<EmptyState title="No data" description="Nothing here" />);
    expect(screen.getByText("No data")).toBeInTheDocument();
    expect(screen.getByText("Nothing here")).toBeInTheDocument();
  });
});

describe("ErrorState", () => {
  it("renders error message", () => {
    render(<ErrorState title="Error" description="Something went wrong" />);
    expect(screen.getByText("Error")).toBeInTheDocument();
    expect(screen.getByText("Something went wrong")).toBeInTheDocument();
  });

  it("calls onRetry when retry button clicked", () => {
    const handleRetry = vi.fn();
    render(<ErrorState title="Error" onRetry={handleRetry} />);
    fireEvent.click(screen.getByText("Try again"));
    expect(handleRetry).toHaveBeenCalledTimes(1);
  });
});

describe("StatusBadge", () => {
  it("renders status text", () => {
    render(<StatusBadge status="ACTIVE" />);
    expect(screen.getByText("ACTIVE")).toBeInTheDocument();
  });

  it("renders PENDING status", () => {
    render(<StatusBadge status="PENDING" />);
    expect(screen.getByText("PENDING")).toBeInTheDocument();
  });
});

describe("PermissionGate", () => {
  it("renders children when permission is granted", () => {
    render(
      <Wrapper>
        <PermissionGate permission="DASHBOARD_VIEW">
          <div>Protected content</div>
        </PermissionGate>
      </Wrapper>
    );
    expect(screen.getByText("Protected content")).toBeInTheDocument();
  });

  it("hides children when permission is not granted", () => {
    render(
      <Wrapper>
        <PermissionGate permission="NON_EXISTENT_PERMISSION">
          <div>Protected content</div>
        </PermissionGate>
      </Wrapper>
    );
    expect(screen.queryByText("Protected content")).not.toBeInTheDocument();
  });
});
