import {
  createStyles,
  MenuItem,
  Select,
  Theme,
  Typography,
  withStyles,
  WithStyles,
} from '@material-ui/core';
import { RouteComponentProps, withRouter } from 'react-router-dom';
import { ListSortOptions } from '../../types';
import React, { Component } from 'react';
import { updateURLParameters } from '../../utils/urlHelper';
import { parseFilterParamsFromQs } from '../../utils/searchFilters';

const styles = (theme: Theme) =>
  createStyles({
    filterButtons: {
      [theme.breakpoints.down('sm')]: {
        fontSize: '0.575rem',
      },
    },
  });

interface OwnProps {
  handleChange: (sortOrder: ListSortOptions) => void;
  isListDynamic?: boolean;
}

interface RouteParams {
  id: string;
}

type Props = OwnProps &
  WithStyles<typeof styles> &
  RouteComponentProps<RouteParams>;

interface State {
  sortOrder: ListSortOptions;
}

export const getSortFromUrlParam = () => {
  return parseFilterParamsFromQs(location.search).sortOrder;
};

class SortDropDown extends Component<Props, State> {
  constructor(props: Props) {
    super(props);

    this.state = {
      ...this.state,
      sortOrder: getSortFromUrlParam(),
    };
  }

  componentDidUpdate = (oldProps: Props, oldState: State) => {
    if (
      oldProps.location.search !== this.props.location.search ||
      oldState.sortOrder !== this.state.sortOrder
    ) {
      this.setState({
        sortOrder: getSortFromUrlParam(),
      });
    }
  };

  isDefaultSort = newSort => {
    const { isListDynamic } = this.props;

    return (
      (isListDynamic && newSort === 'popularity') ||
      (!isListDynamic && newSort === 'added_time') ||
      newSort === 'default'
    );
  };

  updateURLParam = (param, event) => {
    const isNewSortDefault = this.isDefaultSort(event.target.value);
    const isPrevSortDefault = this.isDefaultSort(this.state.sortOrder);
    const newSort = isNewSortDefault ? undefined : event.target.value;

    updateURLParameters(this.props, param, newSort);

    /*
    Only re-fetch data if the sort is actually changing.  Slightly more complicated because of use of additional `default` sort param
    */
    if (
      event.target.value !== this.state.sortOrder &&
      !(isNewSortDefault && isPrevSortDefault)
    ) {
      this.setState(
        {
          sortOrder: event.target.value,
        },
        () => {
          this.props.handleChange(event.target.value);
        },
      );
    }
  };

  render() {
    const { isListDynamic } = this.props;
    const { sortOrder } = this.state;

    return (
      <React.Fragment>
        <Typography display="block">Sort By:</Typography>
        <Select
          value={
            this.state.sortOrder && !this.isDefaultSort(sortOrder)
              ? this.state.sortOrder
              : isListDynamic
              ? 'popularity'
              : 'added_time'
          }
          inputProps={{
            name: 'sortOrder',
            id: 'sort-order',
          }}
          onChange={event => this.updateURLParam('sort', event)}
        >
          <MenuItem value="added_time">Date Added</MenuItem>
          <MenuItem value="popularity">Popularity</MenuItem>
          <MenuItem value="recent">Release Date</MenuItem>
        </Select>
      </React.Fragment>
    );
  }
}

export default withStyles(styles)(withRouter(SortDropDown));
