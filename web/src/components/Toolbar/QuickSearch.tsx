import {
  CircularProgress,
  ClickAwayListener,
  Fade,
  makeStyles,
  MenuList,
  MenuItem,
  Paper,
  Popper,
  Theme,
  Typography,
} from '@material-ui/core';
import _ from 'lodash';
import { ApiItem } from '../../types/v2';
import React from 'react';
import { truncateText } from '../../utils/textHelper';
import RouterLink from '../RouterLink';
import { getTmdbPosterImage } from '../../utils/image-helper';

const useStyles = makeStyles((theme: Theme) => ({
  noResults: {
    margin: theme.spacing(1),
    alignSelf: 'center',
  },
  progressSpinner: {
    margin: theme.spacing(1),
    justifySelf: 'center',
  },
  poster: {
    width: 25,
    boxShadow: '7px 10px 23px -8px rgba(0,0,0,0.57)',
    marginRight: `${theme.spacing(1)}`,
  },
}));

interface Props {
  isSearching: boolean;
  searchAnchor?: HTMLInputElement | null;
  searchResults?: ApiItem[];
  searchText: string;
  handleResetSearchAnchor: (event) => void;
  handleSearchForSubmit: (event) => void;
}

function QuickSearch(props: Props) {
  const classes = useStyles();
  let { searchResults, isSearching, searchText, searchAnchor } = props;

  return searchAnchor && searchText && searchText.length > 0 ? (
    <ClickAwayListener
      onClickAway={event => props.handleResetSearchAnchor(event)}
    >
      <Popper
        open={!!searchAnchor}
        anchorEl={searchAnchor}
        placement="bottom"
        keepMounted
        transition
        disablePortal
      >
        {({ TransitionProps, placement }) => (
          <Fade
            {...TransitionProps}
            style={{
              transformOrigin:
                placement === 'bottom' ? 'center top' : 'center bottom',
            }}
            in={!!searchAnchor}
          >
            <Paper
              id="menu-list-grow"
              style={{
                height: 'auto',
                overflow: 'scroll',
                width: 288,
              }}
            >
              <MenuList
                style={
                  isSearching
                    ? { display: 'flex', justifyContent: 'center' }
                    : {}
                }
              >
                {isSearching ? (
                  <CircularProgress className={classes.progressSpinner} />
                ) : (
                  <React.Fragment>
                    {searchResults && searchResults.length > 0 ? (
                      searchResults.slice(0, 5).map(result => {
                        return (
                          <MenuItem
                            dense
                            component={RouterLink}
                            to={`/${result.type}/${result.slug}`}
                            key={result.id}
                            onClick={event =>
                              props.handleResetSearchAnchor(event)
                            }
                          >
                            <img
                              src={
                                getTmdbPosterImage(result)
                                  ? `https://image.tmdb.org/t/p/w92/${
                                      getTmdbPosterImage(result)!.id
                                    }`
                                  : ''
                              }
                              className={classes.poster}
                            />
                            {truncateText(result.original_title, 30)}
                          </MenuItem>
                        );
                      })
                    ) : (
                      <Typography
                        variant="body1"
                        gutterBottom
                        align="center"
                        className={classes.noResults}
                      >
                        No results :(
                      </Typography>
                    )}
                    {searchResults && searchResults.length > 5 && (
                      <MenuItem
                        dense
                        style={{ justifyContent: 'center' }}
                        onClick={props.handleSearchForSubmit}
                      >
                        View All Results
                      </MenuItem>
                    )}
                  </React.Fragment>
                )}
              </MenuList>
            </Paper>
          </Fade>
        )}
      </Popper>
    </ClickAwayListener>
  ) : null;
}

export default QuickSearch;
