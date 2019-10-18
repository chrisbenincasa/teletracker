import {
  Collapse,
  createStyles,
  Fade,
  Theme,
  Typography,
  withStyles,
  WithStyles,
} from '@material-ui/core';
import React, { Component } from 'react';
import _ from 'lodash';
import { withRouter, RouteComponentProps } from 'react-router-dom';
import { Genre, ItemTypes, ListSortOptions, NetworkTypes } from '../../types';
import TypeToggle, { getTypeFromUrlParam } from './TypeToggle';
import NetworkSelect, { getNetworkTypeFromUrlParam } from './NetworkSelect';
import GenreSelect, { getGenreFromUrlParam } from './GenreSelect';
import SortDropdown, { getSortFromUrlParam } from './SortDropdown';

const styles = (theme: Theme) =>
  createStyles({
    allFiltersContainer: {
      display: 'flex',
      flexWrap: 'wrap',
      margin: `0 -${theme.spacing(2)}px ${theme.spacing(2)}px`,
      padding: 15,
      backgroundColor: '#4E4B47',
    },
    filterSortContainer: {
      [theme.breakpoints.up('sm')]: {
        display: 'flex',
      },
      zIndex: 1000,
      marginBottom: theme.spacing(1),
      flexGrow: 1,
    },
    filterTitle: { display: 'block', width: '100%' },
    genreContainer: {
      display: 'flex',
      flexDirection: 'row',
      width: '50%',
      flexWrap: 'wrap',
    },
    networkTypeContainer: {
      display: 'flex',
      margin: '10px 0',
      alignItems: 'flex-start',
    },
    sortContainer: {
      display: 'flex',
      flexDirection: 'column',
      margin: `${theme.spacing(1)}px`,
      alignItems: 'flex-start',
    },
  });

interface OwnProps {
  handleGenreChange?: (genre?: number[]) => void;
  handleTypeChange: (type?: ItemTypes[]) => void;
  handleNetworkChange: (networkTypes?: NetworkTypes[]) => void;
  handleSortChange: (sortOrder: ListSortOptions) => void;
  isListDynamic?: boolean;
  genres?: Genre[];
  open: boolean;
  showGenre?: boolean;
}

interface RouteParams {
  id: string;
}

type Props = OwnProps &
  WithStyles<typeof styles> &
  RouteComponentProps<RouteParams>;

interface State {
  genresFilter?: number[];
  itemTypes?: ItemTypes[];
  networks?: NetworkTypes[];
  sortOrder: ListSortOptions;
}

class AllFilters extends Component<Props, State> {
  constructor(props: Props) {
    super(props);

    this.state = {
      genresFilter: getGenreFromUrlParam(),
      itemTypes: getTypeFromUrlParam(),
      networks: getNetworkTypeFromUrlParam(),
      sortOrder: getSortFromUrlParam(),
    };
  }

  setGenre = (genres?: number[]) => {
    const { handleGenreChange } = this.props;
    if (handleGenreChange) {
      this.setState(
        {
          genresFilter: genres,
        },
        () => {
          handleGenreChange(genres);
        },
      );
    }
  };

  setType = (type?: ItemTypes[]) => {
    this.setState(
      {
        itemTypes: type,
      },
      () => {
        this.props.handleTypeChange(type);
      },
    );
  };

  setNetworks = (networks?: NetworkTypes[]) => {
    this.setState(
      {
        networks,
      },
      () => {
        this.props.handleNetworkChange(networks);
      },
    );
  };

  setSort = (sortOrder: ListSortOptions) => {
    this.setState(
      {
        sortOrder,
      },
      () => {
        this.props.handleSortChange(sortOrder);
      },
    );
  };

  render() {
    const {
      open,
      handleGenreChange,
      isListDynamic,
      classes,
      genres,
    } = this.props;

    return (
      <Collapse in={open}>
        <Fade in={open}>
          <div className={classes.allFiltersContainer}>
            <Typography
              color="inherit"
              variant="h5"
              className={classes.filterTitle}
            >
              Filter
            </Typography>
            <div className={classes.filterSortContainer}>
              <div className={classes.genreContainer}>
                {handleGenreChange && (
                  <GenreSelect genres={genres} handleChange={this.setGenre} />
                )}
              </div>
              <div className={classes.networkTypeContainer}>
                <NetworkSelect handleChange={this.setNetworks} />
                <TypeToggle handleChange={this.setType} />
              </div>
              <div className={classes.sortContainer}>
                <SortDropdown
                  isListDynamic={!!isListDynamic}
                  handleChange={this.setSort}
                />
              </div>
            </div>
          </div>
        </Fade>
      </Collapse>
    );
  }
}

export default withStyles(styles)(withRouter(AllFilters));
