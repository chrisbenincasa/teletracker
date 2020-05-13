import React from 'react';
import {
  CardMedia,
  Chip,
  ClickAwayListener,
  Collapse,
  Fade,
  Icon,
  LinearProgress,
  makeStyles,
  MenuList,
  MenuItem,
  Paper,
  Popper,
  Theme,
  Typography,
} from '@material-ui/core';
import {
  AccessTime,
  Event,
  Movie,
  SupervisedUserCircle,
  Tv,
} from '@material-ui/icons';
import { Rating } from '@material-ui/lab';
import { Item } from '../../types/v2/Item';
import { truncateText } from '../../utils/textHelper';
import RouterLink from '../RouterLink';
import { getTmdbPosterImage } from '../../utils/image-helper';
import { formatRuntime } from '../../utils/textHelper';
import moment from 'moment';
import { useRouter } from 'next/router';
import ResponsiveImage from '../ResponsiveImage';

const useStyles = makeStyles<Theme, Props>((theme: Theme) => ({
  chip: {
    margin: theme.spacing(0.5, 0.5, 0.5, 0),
    border: 'none',
    textTransform: 'capitalize',
  },
  chipWrapper: {
    display: 'flex',
    flexDirection: 'row',
    alignItems: 'center',
  },
  itemDetails: {
    display: 'flex',
    flexDirection: 'column',
  },
  missingPoster: {
    display: 'flex',
    width: 50,
    marginRight: theme.spacing(1),
    height: 75,
    color: theme.palette.grey[500],
    backgroundColor: theme.palette.grey[300],
    fontSize: '3em',
  },
  missingPosterIcon: {
    alignSelf: 'center',
    margin: '0 auto',
    display: 'inline-block',
  },
  noResults: {
    padding: theme.spacing(1),
    alignSelf: 'center',
  },
  poster: {
    width: 50,
    marginRight: theme.spacing(1),
    overflow: 'hidden',
  },
  searchWrapper: props => ({
    height: 'auto',
    overflow: 'scroll',
    width: '100%',
    maxWidth: 720,
    minHeight: 45,
    marginTop: 10,
    backgroundColor:
      props.color && props.color === 'secondary'
        ? theme.palette.secondary.main
        : theme.palette.primary.main,
  }),
  viewAllResults: {
    justifyContent: 'center',
  },
  popper: {
    zIndex: theme.zIndex.appBar - 1,
  },
}));

interface Props {
  readonly isSearching: boolean;
  readonly searchAnchor?: HTMLInputElement | null;
  readonly searchResults?: Item[];
  readonly searchText: string;
  readonly handleResetSearchAnchor: (event) => void;
  readonly handleSearchForSubmit: (event) => void;
  readonly color?: string;
}

function QuickSearch(props: Props) {
  const classes = useStyles(props);
  const router = useRouter();

  let { searchResults, isSearching, searchText, searchAnchor } = props;

  const handleMenuItemClick = (event, relativeUrl: string) => {
    props.handleResetSearchAnchor(event);
    router.push(relativeUrl);
  };

  return searchAnchor && searchText && searchText.length > 0 ? (
    <ClickAwayListener
      onClickAway={event => props.handleResetSearchAnchor(event)}
    >
      <Popper
        open={!!searchAnchor}
        anchorEl={searchAnchor}
        placement="bottom-start"
        keepMounted
        disablePortal
        transition
        style={{ width: '100%', maxWidth: 720 }}
        className={classes.popper}
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
              className={classes.searchWrapper}
              elevation={5}
            >
              <MenuList
                disablePadding
                style={{ paddingTop: isSearching ? 0 : 4 }}
              >
                <div>
                  <Collapse in={!!searchResults} timeout={500}>
                    {isSearching && <LinearProgress />}
                    {searchResults && searchResults.length > 0
                      ? searchResults.slice(0, 4).map(result => {
                          const voteAverage =
                            result.ratings && result.ratings.length
                              ? result.ratings[0].vote_average
                              : 0;
                          const runtime =
                            (result.runtime &&
                              formatRuntime(result.runtime, result.type)) ||
                            null;
                          const rating =
                            result.release_dates &&
                            result.release_dates.find(item => {
                              if (
                                item.country_code === 'US' &&
                                item.certification !== 'NR'
                              ) {
                                return item.certification;
                              } else {
                                return null;
                              }
                            });

                          return (
                            <MenuItem
                              dense
                              // component={RouterLink}
                              // href={result.relativeUrl}
                              key={result.id}
                              onClick={event =>
                                handleMenuItemClick(event, result.relativeUrl)
                              }
                              style={{ height: 100 }}
                              // onClick={event =>
                              //   props.handleResetSearchAnchor(event)
                              // }
                            >
                              {getTmdbPosterImage(result) ? (
                                <CardMedia
                                  item={result}
                                  component={ResponsiveImage}
                                  imageType="poster"
                                  imageStyle={{
                                    width: 50,
                                    marginRight: 8,
                                    fontSize: '3rem',
                                    maxHeight: 75,
                                  }}
                                />
                              ) : (
                                <div className={classes.missingPoster}>
                                  <Icon
                                    className={classes.missingPosterIcon}
                                    fontSize="inherit"
                                  >
                                    broken_image
                                  </Icon>
                                </div>
                              )}
                              <div className={classes.itemDetails}>
                                <Typography variant="subtitle1">
                                  {truncateText(result.canonicalTitle, 100)}
                                </Typography>
                                <Rating
                                  value={voteAverage / 2}
                                  precision={0.1}
                                  size="small"
                                  readOnly
                                />
                                <div className={classes.chipWrapper}>
                                  <Chip
                                    label={result.type}
                                    variant="outlined"
                                    size="small"
                                    className={classes.chip}
                                    icon={
                                      result.type === 'movie' ? (
                                        <Movie fontSize="small" />
                                      ) : (
                                        <Tv fontSize="small" />
                                      )
                                    }
                                  />
                                  {rating && rating.certification && (
                                    <Chip
                                      label={`Rated ${rating.certification}`}
                                      variant="outlined"
                                      size="small"
                                      className={classes.chip}
                                      icon={
                                        <SupervisedUserCircle fontSize="small" />
                                      }
                                    />
                                  )}
                                  {result.release_date && (
                                    <Chip
                                      label={moment(result.release_date).format(
                                        'YYYY',
                                      )}
                                      variant="outlined"
                                      size="small"
                                      className={classes.chip}
                                      icon={<Event fontSize="small" />}
                                    />
                                  )}
                                  {runtime && (
                                    <Chip
                                      label={runtime}
                                      variant="outlined"
                                      size="small"
                                      className={classes.chip}
                                      icon={<AccessTime fontSize="small" />}
                                    />
                                  )}
                                </div>
                              </div>
                            </MenuItem>
                          );
                        })
                      : !isSearching &&
                        searchText &&
                        searchText.length > 0 && (
                          <Typography
                            variant="body1"
                            align="center"
                            className={classes.noResults}
                          >
                            {`Sorry, no results matching '${searchText}'`}
                          </Typography>
                        )}
                    {searchResults && searchResults.length > 4 && (
                      <MenuItem
                        dense
                        className={classes.viewAllResults}
                        onClick={props.handleSearchForSubmit}
                        key="view-all"
                      >
                        View All Results
                      </MenuItem>
                    )}
                  </Collapse>
                </div>
              </MenuList>
            </Paper>
          </Fade>
        )}
      </Popper>
    </ClickAwayListener>
  ) : null;
}

export default QuickSearch;
