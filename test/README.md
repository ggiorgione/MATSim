# Modal Share Analysis Tools

Automated tools to extract and compare MATSim modal share against targets.

## Files

- `analyze_modal_share.py` - Extract mode counts and percentages from plans file
- `compare_targets.py` - Compare actual modal share to targets; show gaps
- `target_modal_share.csv` - Target modal percentages (editable)
- `run_analysis.ps1` - PowerShell wrapper (activates .venv and runs scripts)

## Setup

```powershell
# First-time setup: create virtual environment if needed
python -m venv ..\.venv
& ..\\.venv\Scripts\Activate.ps1
pip install -r requirements.txt  # If any dependencies needed (currently none)
```

## Usage

### Quick Analysis (Raw Modal %)

```powershell
cd path/to/test
python analyze_modal_share.py "C:\path\to\100.plans.xml.gz"
```

Output example:
```
Analyzing: C:\path\to\100.plans.xml.gz

Modal Share Results (Total legs: 12345)

Mode       Count      Percentage  
--------------------------------
car        6000       48.62%
pt         450        3.64%
walk       4500       36.49%
bike       1260       10.21%
```

### Compare to Targets

```powershell
python compare_targets.py "C:\path\to\100.plans.xml.gz"
```

Output example:
```
Modal Share Comparison (Total legs: 12345)

Mode       Count      Actual     Target     Gap       
--------------------------------------------------
car        6000       48.62%     73.00%    -24.38%
pt         450         3.64%     19.00%    -15.36%
walk       4500       36.49%      6.00%    +30.49%
bike       1260       10.21%      2.00%     +8.21%
--------------------------------------------------

Total absolute gap: 78.44 percentage points
(Goal: < 5 pp gap)
```

### Using PowerShell Wrapper

```powershell
# Analyze only
.\run_analysis.ps1 analyze "C:\path\to\100.plans.xml.gz"

# Compare to targets
.\run_analysis.ps1 compare "C:\path\to\100.plans.xml.gz"
```

## Target Configuration

Edit `target_modal_share.csv` to change target percentages:

```csv
mode,target
car,73
pt,19
walk,6
bike,2
```

## Interpreting Results

- **Gap** = Actual % - Target % 
  - Negative = below target
  - Positive = above target
- **Total Gap** = sum of absolute gaps
  - Goal: < 5 percentage points

## Notes

- Plans file can be either `.xml.gz` (gzipped) or plain `.xml`
- Scripts read leg-level data from plans
- Total legs = sum of all mode counts
- Percentages calculated from leg counts (not trip counts or distance)
