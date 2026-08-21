#!/usr/bin/env bash
# Script to apply Swagger documentation standard to all controllers
# Based on ContactController and WaitlistController as reference implementations

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR/../../.."
CONTROLLERS_DIR="$PROJECT_ROOT/server/smp/src/main/kotlin"

echo "🔍 Finding controllers without proper Swagger documentation..."

# Find all controllers
CONTROLLERS=$(find "$CONTROLLERS_DIR" -name "*Controller.kt" | sort)

echo "📋 Controllers to process:"
echo "$CONTROLLERS" | nl

echo ""
echo "⚠️  WARNING: This script will modify controller files!"
echo "   Make sure you have uncommitted changes backed up."
echo ""
read -p "Continue? (y/N) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Aborted."
    exit 1
fi

# Process each controller - use safe iteration to handle filenames with spaces
while IFS= read -r controller; do
    [[ -z "$controller" ]] && continue
    
    basename=$(basename "$controller")
    
    # Skip already compliant controllers
    if rg -q "@Tag.*description.*endpoints" "$controller" && \
       rg -q "Content.*schema.*Schema.*implementation" "$controller" && \
       rg -q "ProblemDetail" "$controller"; then
        echo "✅ SKIP: $basename (already compliant)"
        continue
    fi
    
    echo "🔧 Processing: $basename"
    
    # Add ProblemDetail import if missing
    if ! rg -q "import org.springframework.http.ProblemDetail" "$controller"; then
        if rg -q "import org.springframework.web.bind.annotation.RestController" "$controller"; then
            sd 'import org.springframework.web.bind.annotation.RestController' \
               'import org.springframework.http.ProblemDetail\nimport org.springframework.web.bind.annotation.RestController' \
               "$controller"
        else
            echo "   ⚠️  WARNING: Cannot add ProblemDetail import - anchor 'RestController' not found in $basename"
        fi
    fi
    
    # Add Content and Schema imports if missing
    if ! rg -q "import io.swagger.v3.oas.annotations.media.Content" "$controller"; then
        if rg -q "import io.swagger.v3.oas.annotations.Operation" "$controller"; then
            sd 'import io.swagger.v3.oas.annotations.Operation' \
               'import io.swagger.v3.oas.annotations.Operation\nimport io.swagger.v3.oas.annotations.media.Content\nimport io.swagger.v3.oas.annotations.media.Schema' \
               "$controller"
        else
            # Try fallback: insert after package declaration
            if rg -q "^package " "$controller"; then
                echo "   ⚠️  WARNING: Operation import not found - attempting to add Content/Schema imports after package"
                # Find the package line and insert imports after any existing imports or after package
                # This requires more complex logic, so we warn the user instead
                echo "   ⚠️  MANUAL ACTION REQUIRED: Add these imports to $basename:"
                echo "      import io.swagger.v3.oas.annotations.media.Content"
                echo "      import io.swagger.v3.oas.annotations.media.Schema"
            else
                echo "   ⚠️  WARNING: Cannot add Content/Schema imports - no suitable anchor found in $basename"
            fi
        fi
    fi
    
    # Add @Tag import if missing
    if ! rg -q "import io.swagger.v3.oas.annotations.tags.Tag" "$controller"; then
        if rg -q "import io.swagger.v3.oas.annotations.responses.ApiResponses" "$controller"; then
            sd 'import io.swagger.v3.oas.annotations.responses.ApiResponses' \
               'import io.swagger.v3.oas.annotations.responses.ApiResponses\nimport io.swagger.v3.oas.annotations.tags.Tag' \
               "$controller"
        else
            echo "   ⚠️  WARNING: Cannot add Tag import - anchor 'ApiResponses' not found in $basename"
            echo "   ⚠️  MANUAL ACTION REQUIRED: Add this import to $basename:"
            echo "      import io.swagger.v3.oas.annotations.tags.Tag"
        fi
    fi
    
    echo "   - Imports added/verified"
    
    # Note: Actual @Tag and Content+Schema additions require more complex logic
    # that would be better done with a proper Kotlin AST parser or manual review
    echo "   ⚠️  Manual review needed for: @Tag annotation and Content schemas"
done < <(printf '%s\n' "$CONTROLLERS")

echo ""
echo "✅ Import updates complete!"
echo ""
echo "📝 Next steps (MANUAL):"
echo "   1. Add @Tag annotation to each controller class"
echo "   2. Add Content(schema = Schema(implementation = ...)) to all @ApiResponse"
echo "   3. Use ProblemDetail::class for error responses"
echo "   4. Run: ./gradlew detektAll"
echo "   5. Run: just backend-test-fast"
echo ""
echo "See: .agents/skills/backend-platform/spring-boot/references/swagger-standard.md for the complete standard"
