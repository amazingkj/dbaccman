import { memo, useState, useCallback, useMemo } from 'react'
import { Input, Select, Space } from 'antd'

export interface SearchColumn {
  key: string
  label: string
}

export interface SearchFilterProps {
  columns: SearchColumn[]
  onSearch: (searchText: string, column: string) => void
  placeholder?: string
  defaultColumn?: string
  allowClear?: boolean
  style?: React.CSSProperties
  inputWidth?: number
  selectWidth?: number
  size?: 'small' | 'middle' | 'large'
}

/**
 * Reusable SearchFilter component for table filtering.
 * Allows searching by specific columns or all columns.
 */
function SearchFilterComponent({
  columns,
  onSearch,
  placeholder = 'Search...',
  defaultColumn = 'all',
  allowClear = true,
  style,
  inputWidth = 200,
  selectWidth = 120,
  size = 'middle',
}: SearchFilterProps) {
  const [searchText, setSearchText] = useState('')
  const [searchColumn, setSearchColumn] = useState(defaultColumn)

  const allColumns = useMemo(
    () => [{ key: 'all', label: 'All' }, ...columns],
    [columns]
  )

  const handleSearchChange = useCallback(
    (value: string) => {
      setSearchText(value)
      onSearch(value, searchColumn)
    },
    [onSearch, searchColumn]
  )

  const handleColumnChange = useCallback(
    (column: string) => {
      setSearchColumn(column)
      onSearch(searchText, column)
    },
    [onSearch, searchText]
  )

  const handleClear = useCallback(() => {
    setSearchText('')
    onSearch('', searchColumn)
  }, [onSearch, searchColumn])

  return (
    <Space style={style}>
      <Select
        value={searchColumn}
        onChange={handleColumnChange}
        style={{ width: selectWidth }}
        size={size}
        aria-label="Select search column"
      >
        {allColumns.map((col) => (
          <Select.Option key={col.key} value={col.key}>
            {col.label}
          </Select.Option>
        ))}
      </Select>
      <Input.Search
        placeholder={placeholder}
        allowClear={allowClear}
        value={searchText}
        onChange={(e) => handleSearchChange(e.target.value)}
        onClear={handleClear}
        style={{ width: inputWidth }}
        size={size}
        aria-label={`Search by ${allColumns.find((c) => c.key === searchColumn)?.label || 'all'}`}
      />
    </Space>
  )
}

export const SearchFilter = memo(SearchFilterComponent)

export default SearchFilter
